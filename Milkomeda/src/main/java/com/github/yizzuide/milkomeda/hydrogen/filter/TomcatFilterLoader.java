package com.github.yizzuide.milkomeda.hydrogen.filter;

import com.github.yizzuide.milkomeda.hydrogen.core.HydrogenHolder;
import com.github.yizzuide.milkomeda.hydrogen.core.HydrogenProperties;
import com.github.yizzuide.milkomeda.universe.polyfill.TomcatPolyfill;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.core.ApplicationContext;
import org.apache.catalina.core.ApplicationContextFacade;
import org.apache.catalina.core.ApplicationFilterFactory;
import org.apache.catalina.core.StandardContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.CollectionUtils;

import javax.servlet.Filter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TomcatFilterLoader
 * Tomcat过滤器加载器
 *
 * @author yizzuide
 * @since 3.0.0
 * @see ApplicationContextFacade#addFilter(java.lang.String, java.lang.String)
 * @see ApplicationContext#addFilter(java.lang.String, java.lang.Class)
 * @see StandardContext#filterStart()
 * @see ApplicationFilterFactory#createFilterChain(javax.servlet.ServletRequest, org.apache.catalina.Wrapper, javax.servlet.Servlet)
 * Create at 2020/04/01 18:19
 */
@Slf4j
public class TomcatFilterLoader extends AbstractFilterLoader {
    /**
     * 初始化预加载过滤器
     */
    private List<HydrogenProperties.Filters> filtersList;
    /**
     * 加载过的过滤器
     */
    private List<HydrogenProperties.Filters> loadFiltersList;

    public boolean load(String name, Class<? extends Filter> clazz, String... urlPatterns) {
        // 初始时调用加载
        if (getApplicationContext() == null) {
            if (filtersList == null) {
                filtersList = new ArrayList<>();
            }
            if (urlPatterns == null) {
                urlPatterns =  new String[] {"/*"};
            }
            HydrogenProperties.Filters filters = new HydrogenProperties.Filters();
            filters.setName(name);
            filters.setClazz(clazz);
            filters.setUrlPatterns(Arrays.asList(urlPatterns));
            filtersList.add(filters);
            return true;
        }
        // 动态加载
        return TomcatPolyfill.addDynamicFilter(getServletContext(), (ConfigurableApplicationContext) getApplicationContext(), name, clazz, urlPatterns);
    }

    public boolean unload(String name) {
        return TomcatPolyfill.removeDynamicFilter(name, getServletContext());
    }

    @Override
    protected void refresh() {
        // 初始化Filter加载
        if (filtersList != null) {
            for (HydrogenProperties.Filters initFilters : filtersList) {
                load(initFilters.getName(), initFilters.getClazz(), initFilters.getUrlPatterns().toArray(new String[0]));
            }
            // clear...
            filtersList.clear();
            filtersList = null;
        }

        // 配置动态加载
        List<HydrogenProperties.Filters> filtersList = HydrogenHolder.getProps().getFilter().getFilters();
        if (CollectionUtils.isEmpty(filtersList)) {
            loadFiltersList.forEach(f -> this.unload(f.getName()));
            loadFiltersList = null;
        }
        if (!CollectionUtils.isEmpty(loadFiltersList)) {
            // 需删除过滤器
            loadFiltersList.stream().filter(f -> !filtersList.contains(f)).collect(Collectors.toList())
                    .forEach(f -> this.unload(f.getName()));
        }
        // 需要添加的过滤器
        filtersList.stream().filter(f -> !loadFiltersList.contains(f)).forEach(f ->
                this.load(f.getName(), f.getClazz(), f.getUrlPatterns().toArray(new String[0])));
        // 记录最新配置
        loadFiltersList = filtersList;
    }
}
