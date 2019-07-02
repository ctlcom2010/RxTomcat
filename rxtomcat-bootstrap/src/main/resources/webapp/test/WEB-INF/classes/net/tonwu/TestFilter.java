package net.tonwu;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.log4j.Logger;

public class TestFilter implements Filter {
    static Logger log = Logger.getLogger(TestFilter.class);
    
    public TestFilter() {
    }

	public void destroy() {
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		// place your code here
	    log.info("处理请求前调用 Filter - " + this);
		// pass the request along the filter chain
		chain.doFilter(request, response);
		log.info("返回响应前调用 Filter - " + this);
	}

    @Override
    public void init(FilterConfig arg0) throws ServletException {
    }
}
