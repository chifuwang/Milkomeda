package com.github.yizzuide.milkomeda.ice;

import com.github.yizzuide.milkomeda.universe.config.MilkomedaContextConfig;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * IceServerConfig
 *
 * @author yizzuide
 * @since 1.15.2
 * @version 1.18.2
 * Create at 2019/11/21 11:14
 */
@Configuration
@Import(MilkomedaContextConfig.class)
@ConditionalOnClass(RedisTemplate.class)
@AutoConfigureAfter(RedisAutoConfiguration.class)
@EnableConfigurationProperties(IceProperties.class)
public class IceServerConfig extends IceBasicConfig {
    @Bean
    public DelayTimer delayTimer() {
        return new DelayTimer();
    }
}
