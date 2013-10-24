/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.weld.ejb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InterceptionType;
import javax.enterprise.inject.spi.Interceptor;
import javax.interceptor.InvocationContext;

import org.jboss.as.ee.component.Component;
import org.jboss.as.ee.component.ComponentInstanceInterceptorFactory;
import org.jboss.as.ejb3.component.stateful.SerializedCdiInterceptorsKey;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ValueManagedReference;
import org.jboss.as.weld.WeldBootstrapService;
import org.jboss.as.weld.services.bootstrap.WeldEjbServices;
import org.jboss.invocation.InterceptorContext;
import org.jboss.invocation.InterceptorFactoryContext;
import org.jboss.msc.value.ImmediateValue;
import org.jboss.msc.value.InjectedValue;
import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedType;
import org.jboss.weld.annotated.slim.SlimAnnotatedType;
import org.jboss.weld.bean.interceptor.InterceptorBindingsAdapter;
import org.jboss.weld.ejb.spi.EjbDescriptor;
import org.jboss.weld.ejb.spi.EjbServices;
import org.jboss.weld.ejb.spi.InterceptorBindings;
import org.jboss.weld.ejb.spi.helpers.ForwardingEjbServices;
import org.jboss.weld.injection.producer.InterceptionModelInitializer;
import org.jboss.weld.interceptor.spi.metadata.ClassMetadata;
import org.jboss.weld.interceptor.spi.model.InterceptionModel;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.resources.ClassTransformer;
import org.jboss.weld.serialization.spi.ContextualStore;
import org.jboss.weld.serialization.spi.helpers.SerializableContextualInstance;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Interceptor for applying the JSR-299 specific interceptor bindings.
 * <p/>
 * It is a separate interceptor, as it needs to be applied after all
 * the other existing interceptors.
 *
 * @author Marius Bogoevici
 * @author Stuart Douglas
 * @author Jozef Hartinger
 */
public class Jsr299BindingsInterceptor implements org.jboss.invocation.Interceptor {

    private final Map<String, SerializableContextualInstance<Interceptor<Object>, Object>> interceptorInstances;
    private final CreationalContext<Object> creationalContext;
    private final InterceptionType interceptionType;
    private final InterceptorBindings interceptorBindings;


    protected Jsr299BindingsInterceptor(final BeanManagerImpl beanManager, final InterceptorBindings interceptorBindings, final Bean<?> bean, final InterceptorFactoryContext context, final InterceptionType interceptionType, final ClassLoader classLoader) {
        final ClassLoader tccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            //this is not always called with the deployments TCCL set
            //which causes weld to blow up
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
            this.interceptionType = interceptionType;
            this.interceptorBindings = interceptorBindings;

            final AtomicReference<ManagedReference> reference = (AtomicReference<ManagedReference>) context.getContextData().get(SerializedCdiInterceptorsKey.class);

            if (reference == null) {
                creationalContext = (CreationalContext<Object>) beanManager.createCreationalContext(bean);
                interceptorInstances = new HashMap<String, SerializableContextualInstance<Interceptor<Object>, Object>>();
                if (interceptorBindings != null) {
                    for (Interceptor<?> interceptor : interceptorBindings.getAllInterceptors()) {
                        addInterceptorInstance((Interceptor<Object>) interceptor, beanManager, interceptorInstances);
                    }
                }
                WeldInterceptorInstances instances = new WeldInterceptorInstances(creationalContext, interceptorInstances);
                context.getContextData().put(SerializedCdiInterceptorsKey.class, new AtomicReference<ManagedReference>(new ValueManagedReference(new ImmediateValue<Object>(instances))));
            } else {
                final WeldInterceptorInstances instances = (WeldInterceptorInstances) reference.get().getInstance();
                creationalContext = instances.getCreationalContext();
                interceptorInstances = instances.getInterceptorInstances();
            }
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(tccl);
        }

    }


    protected Object delegateInterception(InvocationContext invocationContext, InterceptionType interceptionType, List<Interceptor<?>> currentInterceptors)
            throws Exception {
        List<Object> currentInterceptorInstances = new ArrayList<Object>();
        for (Interceptor<?> interceptor : currentInterceptors) {
            currentInterceptorInstances.add(interceptorInstances.get(interceptor.getBeanClass().getName()).getInstance());
        }
        if (currentInterceptorInstances.size() > 0) {
            return new DelegatingInterceptorInvocationContext(invocationContext, currentInterceptors, currentInterceptorInstances, interceptionType).proceed();
        } else {
            return invocationContext.proceed();
        }

    }


    private Object doMethodInterception(InvocationContext invocationContext, InterceptionType interceptionType)
            throws Exception {
        if (interceptorBindings != null) {
            List<Interceptor<?>> currentInterceptors = interceptorBindings.getMethodInterceptors(interceptionType, invocationContext.getMethod());
            return delegateInterception(invocationContext, interceptionType, currentInterceptors);
        } else {
            return invocationContext.proceed();
        }
    }

    @Override
    public Object processInvocation(final InterceptorContext context) throws Exception {
        switch (interceptionType) {
            case AROUND_INVOKE:
                return doMethodInterception(context.getInvocationContext(), InterceptionType.AROUND_INVOKE);
            case AROUND_TIMEOUT:
                return doMethodInterception(context.getInvocationContext(), InterceptionType.AROUND_TIMEOUT);
            case PRE_DESTROY:
                try {
                    return doLifecycleInterception(context);
                } finally {
                    creationalContext.release();
                }
            case POST_CONSTRUCT:
                return doLifecycleInterception(context);
            case AROUND_CONSTRUCT:
                return doLifecycleInterception(context);
            default:
                //should never happen
                return context.proceed();
        }
    }

    private Object doLifecycleInterception(final InterceptorContext context) throws Exception {
        try {
            if (interceptorBindings != null) {
                List<Interceptor<?>> currentInterceptors = interceptorBindings.getLifecycleInterceptors(interceptionType);
                delegateInterception(context.getInvocationContext(), interceptionType, currentInterceptors);
            }
        } finally {
            return context.proceed();
        }
    }

    private void addInterceptorInstance(Interceptor<Object> interceptor, BeanManagerImpl beanManager, Map<String, SerializableContextualInstance<Interceptor<Object>, Object>> instances) {
        Object instance = beanManager.getContext(interceptor.getScope()).get(interceptor, creationalContext);
        SerializableContextualInstance<Interceptor<Object>, Object> serializableContextualInstance
                = beanManager.getServices().get(ContextualStore.class).<Interceptor<Object>, Object>getSerializableContextualInstance(interceptor, instance, creationalContext);
        instances.put(interceptor.getBeanClass().getName(), serializableContextualInstance);
    }


    public static class Factory extends ComponentInstanceInterceptorFactory {

        private final InjectedValue<WeldBootstrapService> weldContainer = new InjectedValue<WeldBootstrapService>();
        private final String beanArchiveId;
        private final String ejbName;
        private final Class<?> componentClass;
        private final InterceptionType interceptionType;
        private final ClassLoader classLoader;

        public Factory(final String beanArchiveId, final String ejbName, final Class<?> componentClass, final InterceptionType interceptionType, final ClassLoader classLoader) {
            this.beanArchiveId = beanArchiveId;
            this.ejbName = ejbName;
            this.componentClass = componentClass;
            this.interceptionType = interceptionType;
            this.classLoader = classLoader;
        }

        @Override
        public org.jboss.invocation.Interceptor create(final Component component, final InterceptorFactoryContext context) {
            BeanManagerImpl manager = weldContainer.getValue().getBeanManager(beanArchiveId);

            //we use the interception type as the context key
            //as there are potentially up to six instances of this interceptor for every component
            if (ejbName != null) {
                EjbDescriptor<?> ejbDescriptor = manager.getEjbDescriptor(ejbName);
                EjbServices ejbServices = manager.getServices().get(EjbServices.class);
                if (ejbServices instanceof ForwardingEjbServices) {
                    ejbServices = ((ForwardingEjbServices) ejbServices).delegate();
                }
                InterceptorBindings interceptorBindings = null;
                if (ejbServices instanceof WeldEjbServices) {
                    interceptorBindings = ((WeldEjbServices) ejbServices).getBindings(ejbName);
                }
                return new Jsr299BindingsInterceptor(manager, interceptorBindings, manager.getBean(ejbDescriptor), context, interceptionType, classLoader);
            } else {
                // This is a managed bean
                SlimAnnotatedType<?> type = (SlimAnnotatedType<?>) manager.createAnnotatedType(componentClass);
                if (!manager.getInterceptorModelRegistry().containsKey(type)) {
                    EnhancedAnnotatedType<?> enhancedType = manager.getServices().get(ClassTransformer.class).getEnhancedAnnotatedType(type);
                    InterceptionModelInitializer.of(manager, enhancedType, null).init();
                }
                InterceptionModel<ClassMetadata<?>> model = manager.getInterceptorModelRegistry().get(type);
                final InterceptorBindings bindings = (model != null) ? new InterceptorBindingsAdapter(manager.getInterceptorModelRegistry().get(type)) : null;
                return new Jsr299BindingsInterceptor(manager, bindings, null, context, interceptionType, classLoader);
            }
        }

        public InjectedValue<WeldBootstrapService> getWeldContainer() {
            return weldContainer;
        }
    }
}
