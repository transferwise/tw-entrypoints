package com.transferwise.common.entrypoints.twtasks;

import com.transferwise.common.baseutils.context.TwContext;
import com.transferwise.tasks.processing.ITaskProcessingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EntryPointsTwTasksConfiguration {
    @Bean
    public ITaskProcessingInterceptor entryPointsTasksProcessingInterceptor() {
        return (task, processor) -> TwContext.newSubContext().asEntryPoint("TwTasks", task.getType())
                                             .execute(processor);
    }
}
