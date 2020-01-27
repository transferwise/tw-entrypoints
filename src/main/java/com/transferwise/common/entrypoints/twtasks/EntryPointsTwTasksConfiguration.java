package com.transferwise.common.entrypoints.twtasks;

import com.transferwise.common.entrypoints.EntryPoints;
import com.transferwise.tasks.processing.ITaskProcessingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EntryPointsTwTasksConfiguration {
    @Autowired
    private EntryPoints entryPoints;

    @Bean
    public ITaskProcessingInterceptor entryPointsTasksProcessingInterceptor() {
        return (task, processor) -> entryPoints.of("TwTasks", task.getType()).execute(processor);
    }
}
