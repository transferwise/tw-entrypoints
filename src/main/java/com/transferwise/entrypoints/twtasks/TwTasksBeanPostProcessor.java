package com.transferwise.entrypoints.twtasks;

import com.transferwise.entrypoints.EntryPoints;
import com.transferwise.tasks.domain.Task;
import com.transferwise.tasks.handler.interfaces.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;

public class TwTasksBeanPostProcessor implements BeanPostProcessor {
    @Autowired
    private BeanFactory beanFactory;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ITaskHandler) {
            return new EntryPointAwareTaskHandler((ITaskHandler) bean, beanFactory.getBean(EntryPoints.class));
        }
        return bean;
    }

    public static class EntryPointAwareTaskHandler implements ITaskHandler {
        private ITaskHandler delegate;
        private EntryPoints entryPoints;

        public EntryPointAwareTaskHandler(ITaskHandler taskHandler, EntryPoints entryPoints) {
            this.delegate = taskHandler;
            this.entryPoints = entryPoints;
        }

        @Override
        public ITaskProcessor getProcessor() {
            ITaskProcessor processor = delegate.getProcessor();
            if (processor instanceof ISyncTaskProcessor) {
                return new EntryPointAwareSyncTaskProcessor((ISyncTaskProcessor) processor, entryPoints);
            } else if (processor instanceof IAsyncTaskProcessor) {
                return new EntryPointAwareAsyncTaskProcessor((IAsyncTaskProcessor) processor, entryPoints);
            }
            return processor;
        }

        @Override
        public ITaskRetryPolicy getRetryPolicy() {
            return delegate.getRetryPolicy();
        }

        @Override
        public ITaskConcurrencyPolicy getConcurrencyPolicy() {
            return delegate.getConcurrencyPolicy();
        }

        @Override
        public ITaskProcessingPolicy getProcessingPolicy() {
            return delegate.getProcessingPolicy();
        }

        @Override
        public boolean handles(String type) {
            return delegate.handles(type);
        }
    }

    public static class EntryPointAwareSyncTaskProcessor implements ISyncTaskProcessor {
        private ISyncTaskProcessor delegate;
        private EntryPoints entryPoints;

        public EntryPointAwareSyncTaskProcessor(ISyncTaskProcessor delegate, EntryPoints entryPoints) {
            this.entryPoints = entryPoints;
            this.delegate = delegate;
        }

        @Override
        public ProcessResult process(Task task) {
            return entryPoints.inEntryPointContext("TwTasks_" + task.getType(), () -> delegate.process(task));
        }

        @Override
        public boolean isTransactional(Task task) {
            return delegate.isTransactional(task);
        }
    }

    public static class EntryPointAwareAsyncTaskProcessor implements IAsyncTaskProcessor {
        private IAsyncTaskProcessor delegate;
        private EntryPoints entryPoints;

        public EntryPointAwareAsyncTaskProcessor(IAsyncTaskProcessor delegate, EntryPoints entryPoints) {
            this.entryPoints = entryPoints;
            this.delegate = delegate;
        }

        @Override
        public void process(Task task, Runnable successCallback, Runnable errorCallback) {
            entryPoints.inEntryPointContext("TwTasks_" + task.getType(), () -> {
                delegate.process(task, successCallback, errorCallback);
                return null;
            });
        }
    }

}
