package io.github.slimanitz.medirdv.notification;

import io.github.slimanitz.medirdv.events.Topics;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.retrytopic.DestinationTopic;
import org.springframework.kafka.retrytopic.RetryTopicComponentFactory;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationSupport;
import org.springframework.kafka.retrytopic.RetryTopicNamesProviderFactory;
import org.springframework.kafka.retrytopic.SuffixingRetryTopicNamesProviderFactory;

/**
 * Names the dead letter topic {@code notification.dlt} instead of the default
 * {@code <source topic>-dlt}, so operators have a single place to look at.
 */
@Configuration(proxyBeanMethods = false)
public class DeadLetterTopicConfiguration extends RetryTopicConfigurationSupport {

    @Override
    protected RetryTopicComponentFactory createComponentFactory() {
        return new RetryTopicComponentFactory() {
            @Override
            public RetryTopicNamesProviderFactory retryTopicNamesProviderFactory() {
                return new SuffixingRetryTopicNamesProviderFactory() {
                    @Override
                    public RetryTopicNamesProvider createRetryTopicNamesProvider(DestinationTopic.Properties properties) {
                        if (properties.isDltTopic()) {
                            return new SuffixingRetryTopicNamesProvider(properties) {
                                @Override
                                public String getTopicName(String topic) {
                                    return Topics.NOTIFICATION_DLT;
                                }
                            };
                        }
                        return super.createRetryTopicNamesProvider(properties);
                    }
                };
            }
        };
    }
}
