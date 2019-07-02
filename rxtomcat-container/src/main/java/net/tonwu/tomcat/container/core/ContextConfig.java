/**
 * Copyright 2019 tonwu.net - 顿悟源码
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tonwu.tomcat.container.core;

import java.io.File;
import java.io.FileInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import net.tonwu.tomcat.container.Lifecycle.LifecycleEvent;
import net.tonwu.tomcat.container.Lifecycle.LifecycleListener;
import net.tonwu.tomcat.util.digester.Digester;

/**
 * 容器 Context 声明周期监听类，用于配置和启动
 * 
 * @author tonwu.net
 */
public class ContextConfig implements LifecycleListener {
    final static Logger log = LoggerFactory.getLogger(ContextConfig.class);
    
    /** 管理的 Context */
    private Context context;
    private boolean deployed = false;
    
    /** 用于解析 web.xml 的 Digester */
    private Digester webXmlParser;
    
    @Override
    public void lifecycleEvent(LifecycleEvent event) throws Exception {
        context = (Context) event.getLifecycle();
        switch (event.getType()) {
        case INIT:
            init();
            break;
        case START:
            deployApp();
            break;
        case STOP:
            stop();
            break;
        }
    }

    private void init() {
        webXmlParser = new Digester();
        webXmlParser.setClassLoader(context.getConatinerClassLoader());
        
        webXmlParser.addCallMethod("web-app/context-param","addParameter", 2);
        webXmlParser.addCallParam("web-app/context-param/param-name", 0);
        webXmlParser.addCallParam("web-app/context-param/param-value", 1);
        
        webXmlParser.addCallMethod("web-app/distributable", "setDistributable", 0, new Class[]{Boolean.TYPE});
        
        webXmlParser.addObjectCreate("web-app/filter", "net.tonwu.tomcat.container.servletx.FilterWrapper");
        webXmlParser.addCallMethod("web-app/filter/filter-class", "setFilterClass", 0);
        webXmlParser.addCallMethod("web-app/filter/filter-name", "setFilterName", 0);
        webXmlParser.addSetNext("web-app/filter","addFilterWrapper");
        
        webXmlParser.addCallMethodMultiRule("web-app/filter-mapping","addFilterMapping", 2, 1);
        webXmlParser.addCallParam("web-app/filter-mapping/filter-name",0);
        webXmlParser.addCallParamMultiRule("web-app/filter-mapping/url-pattern", 1);
        
        webXmlParser.addObjectCreate("web-app/servlet","net.tonwu.tomcat.container.core.Wrapper");
        webXmlParser.addCallMethod("web-app/servlet/servlet-class", "setServletClass", 0);
        webXmlParser.addCallMethod("web-app/servlet/servlet-name", "setName", 0);
        webXmlParser.addSetNext("web-app/servlet", "addChild", "net.tonwu.tomcat.container.Container");
        
        webXmlParser.addCallMethodMultiRule("web-app/servlet-mapping","addServletMapping", 2, 1);
        webXmlParser.addCallParam("web-app/servlet-mapping/servlet-name", 0);
        webXmlParser.addCallParamMultiRule("web-app/servlet-mapping/url-pattern", 1);
    }
    
    /**
     * 部署过程中，目前不会直接加载 Servlet 或者 Filter
     */
    private void deployApp() throws Exception {
        File appBase = new File(System.getProperty("rxtomcat.base"), context.getAppBase());
        File[] apps = appBase.listFiles();
        if (apps == null || apps.length == 0) {
            throw new IllegalArgumentException("必须在[" + System.getProperty("rxtomcat.base") + "]部署且只能部署 一个 web 应用才能启动");
        }
        
        if (apps.length > 1) {
            throw new IllegalArgumentException("只支持部署一个 web 应用");
        }
        
        if (!deployed) {
            File docBase = apps[0];
            
            context.setDocBase(docBase.getName());
            context.setDocBasePath(docBase.getAbsolutePath());
            
            File webXml = new File(docBase, Context.AppWebXml);
            InputSource in = new InputSource(new FileInputStream(webXml));
            webXmlParser.push(context);
            webXmlParser.parse(in);
            deployed = true;
            
            log.info("部署 Web 应用 [/{}]", context.getDocBase());
        }
    }
    
    private void stop() {
    }
}