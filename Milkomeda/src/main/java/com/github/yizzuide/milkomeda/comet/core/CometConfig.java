package com.github.yizzuide.milkomeda.comet.core;

import com.github.yizzuide.milkomeda.pulsar.PulsarConfig;
import com.github.yizzuide.milkomeda.universe.config.MilkomedaProperties;
import com.github.yizzuide.milkomeda.universe.polyfill.SpringMvcPolyfill;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * CometConfig
 *
 * @author yizzuide
 * @since 2.0.0
 * @version 3.0.1
 * Create at 2019/12/12 18:10
 */
@Configuration
@AutoConfigureAfter(PulsarConfig.class)
@EnableConfigurationProperties({MilkomedaProperties.class, CometProperties.class})
public class CometConfig {

    @Autowired CometProperties cometProperties;

    @Autowired
    public void config(CometProperties cometProperties) {
        CometHolder.setProps(cometProperties);
    }

    @Bean
    public CometAspect cometAspect() {
        return new CometAspect();
    }

    @Bean
    @ConditionalOnProperty(prefix = "milkomeda.comet", name = "enable-read-request-body", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<CometRequestFilter> cometRequestFilter() {
        FilterRegistrationBean<CometRequestFilter> cometRequestFilter = new FilterRegistrationBean<>();
        cometRequestFilter.setFilter(new CometRequestFilter());
        cometRequestFilter.setName("cometRequestFilter");
        cometRequestFilter.setUrlPatterns(Collections.singleton("/*"));
        cometRequestFilter.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return cometRequestFilter;
    }

    // 配置RequestMappingHandlerAdapter实现动态注解SpringMVC处理器组件
    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public void configParamResolve(RequestMappingHandlerAdapter adapter) {
        List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList<>();
        // 动态添加针对注解 @CometParam 处理的解析器
        argumentResolvers.add(new CometParamResolver());
        argumentResolvers.addAll(Objects.requireNonNull(adapter.getArgumentResolvers()));
        adapter.setArgumentResolvers(argumentResolvers);
    }

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public void configRequestMappingHandlerMapping(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        // 使用内置拦截器
        SpringMvcPolyfill.addDynamicInterceptor(cometInterceptor(),  Ordered.HIGHEST_PRECEDENCE, Collections.singletonList("/**"),
                null, requestMappingHandlerMapping);
    }

    @Bean
    public CometInterceptor cometInterceptor() {
        return new CometInterceptor();
    }
}
