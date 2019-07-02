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
package net.tonwu.tomcat.util.digester;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import net.tonwu.tomcat.util.digester.rule.CallMethodMultiRule;
import net.tonwu.tomcat.util.digester.rule.CallMethodRule;
import net.tonwu.tomcat.util.digester.rule.CallParamMultiRule;
import net.tonwu.tomcat.util.digester.rule.CallParamRule;
import net.tonwu.tomcat.util.digester.rule.ObjectCreateRule;
import net.tonwu.tomcat.util.digester.rule.SetFieldsRule;
import net.tonwu.tomcat.util.digester.rule.SetNextRule;

/**
 * 依据配置好的规则使用 Sax 解析 XML，只对节点名和属性处理，不处理内容
 * 
 * @author tonwu.net
 */
public class Digester extends DefaultHandler {

    final static Logger log = LoggerFactory.getLogger(Digester.class);

    private Object root;

    /** 默认使用 Thread.currentThread().getContextClassLoader() */
    protected ClassLoader classLoader;

    /** 对象栈 */
    private LinkedList<Object> stack = new LinkedList<>();

    /** 解析过程中节点对应匹配的规则 */
    private LinkedList<List<Rule>> matches = new LinkedList<>();

    /** 当前节点元素包含的文本 */
    private LinkedList<StringBuilder> bodyTexts = new LinkedList<>();

    /** 配置的规则 */
    private HashMap<String, List<Rule>> rules = new HashMap<String, List<Rule>>();

    /** 当前匹配的 pattern */
    private String match = "";

    public Object parse(InputSource input) throws IOException, SAXException {
        getXMLReader().parse(input);
        return root;
    }

    private XMLReader getXMLReader() {
        XMLReader reader = null;
        try {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            reader = parser.getXMLReader();
        } catch (Exception e) {
            log.error("", e);
        }
        reader.setContentHandler(this);
        return reader;
    }

    // Sax method
    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        log.debug("startElement({},{},{})", uri, localName, qName);
        
        // 入栈一个保存元素节点内容的 StringBuilder
        bodyTexts.push(new StringBuilder());
        StringBuilder sb = new StringBuilder(match);
        if (match.length() > 0) {
            sb.append('/');
        }
        sb.append(qName);
        match = sb.toString();
        log.debug("  New match='{}'", match);
        
        List<Rule> matchRules = matchRules(match);
        matches.push(matchRules);
        if (matchRules != null && matchRules.size() > 0) {
            for (int i = 0; i < matchRules.size(); i++) {
                try {
                    Rule rule = matchRules.get(i);
                    log.debug("  Fire begin() for {}", rule);
                    rule.begin(uri, qName, attributes);
                } catch (Exception e) {
                    log.error("Begin event threw exception", e);
                    throw new SAXException(e);
                }
            }
        } else {
            log.debug("  No rules found matching '{}'.", match);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        log.debug("characters(...)");
        
        // 获取当前元素节点关联的 StringBuilder，添加内容
        StringBuilder bodyText = bodyTexts.peek();
        bodyText.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        List<Rule> matchRules = matches.pop();
        StringBuilder bodyText = bodyTexts.pop();
        if (log.isDebugEnabled()) {
            log.debug("endElement({},{},{})", uri, localName, qName);
            log.debug("  match='{}'", match);
            log.debug("  bodyText='{}'", bodyText.toString().trim());
        }
        if (matchRules != null && matchRules.size() > 0) {
            for (int i = 0; i < matchRules.size(); i++) {
                try {
                    Rule rule = matchRules.get(i);
                    log.debug("  Fire body() for {}", rule);
                    rule.body(uri, qName, bodyText.toString());
                } catch (Exception e) {
                    log.error("Body event threw exception", e);
                    throw new SAXException(e);
                }
            }
        } else {
            log.debug("  No rules found matching '{}'.", match);
        }

        for (int i = 0; i < matchRules.size(); i++) { // 倒叙遍历
            int j = (matchRules.size() - 1) - i;
            try {
                Rule rule = matchRules.get(j);
                log.debug("  Fire end() for " + rule);
                rule.end(uri, qName);
            } catch (Exception e) {
                log.error("End event threw exception", e);
                throw new SAXException(e);
            }
        }
        // 恢复上一个匹配的表达式
        int slash = match.lastIndexOf('/');
        if (slash >= 0) {
            match = match.substring(0, slash);
        } else {
            match = "";
        }
    }

    @Override
    public void endDocument() throws SAXException {
        match = "";
        stack.clear();
    }
    // End Sax method

    public String match() {
        return match;
    }

    // 对象栈的操作
    public void push(Object obj) {
        if (stack.size() == 0) {
            root = obj;
        }
        stack.push(obj);
    }

    public Object pop() {
        return stack.pop();
    }

    public Object peek() {
        return stack.peek();
    }

    public Object peek(int index) {
        return stack.get(index);
    }

    public void addSetFields(String pattern) {
        SetFieldsRule setFieldsRule = new SetFieldsRule();
        setFieldsRule.setDigester(this);
        addRule(pattern, setFieldsRule);
    }

    public void addObjectCreate(String pattern, String clazz) {
        ObjectCreateRule objectCreateRule = new ObjectCreateRule(clazz);
        objectCreateRule.setDigester(this);
        addRule(pattern, objectCreateRule);
    }

    public void addSetNext(String pattern, String methodName) {
        SetNextRule setNextRule = new SetNextRule(methodName);
        setNextRule.setDigester(this);
        addRule(pattern, setNextRule);
    }

    public void addSetNext(String pattern, String methodName, String paramType) {
        SetNextRule setNextRule = new SetNextRule(methodName, paramType);
        setNextRule.setDigester(this);
        addRule(pattern, setNextRule);
    }

    public void addCallMethod(String pattern, String methodName, int paramCount) {
        addCallMethod(pattern, methodName, paramCount, null);
    }

    public void addCallMethod(String pattern, String methodName, int paramCount, Class<?>[] paramsType) {
        CallMethodRule callMethod = new CallMethodRule(methodName, paramCount, paramsType);
        callMethod.setDigester(this);
        addRule(pattern, callMethod);
    }

    public void addCallParam(String pattern, int paramIndex) {
        addCallParam(pattern, paramIndex, null);
    }

    public void addCallParam(String pattern, int paramIndex, String attributeName) {
        CallParamRule callParam = new CallParamRule(paramIndex, attributeName);
        callParam.setDigester(this);
        addRule(pattern, callParam);
    }

    public void addCallMethodMultiRule(String pattern, String methodName, int paramCount, int multiParamIndex) {
        CallMethodMultiRule callMethodMulti = new CallMethodMultiRule(methodName, paramCount, multiParamIndex);
        callMethodMulti.setDigester(this);
        addRule(pattern, callMethodMulti);
    }

    public void addCallParamMultiRule(String pattern, int paramIndex) {
        CallParamMultiRule callParamMulti = new CallParamMultiRule(paramIndex);
        callParamMulti.setDigester(this);
        addRule(pattern, callParamMulti);
    }

    // rules
    public void addRule(String pattern, Rule rule) {
        // to help users who accidently add '/' to the end of their patterns
        int patternLength = pattern.length();
        if (patternLength > 1 && pattern.endsWith("/")) {
            pattern = pattern.substring(0, patternLength - 1);
        }

        List<Rule> list = rules.get(pattern);
        if (list == null) {
            list = new ArrayList<Rule>();
            rules.put(pattern, list);
        }
        list.add(rule);
    }

    public List<Rule> matchRules(String pattern) {
        List<Rule> rulesList = rules.get(pattern);

        if ((rulesList == null) || (rulesList.size() < 1)) {
            // Find the longest key, ie more discriminant
            String longKey = "";
            Iterator<String> keys = rules.keySet().iterator();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.startsWith("*/")) {
                    if (pattern.equals(key.substring(2)) || pattern.endsWith(key.substring(1))) {
                        if (key.length() > longKey.length()) {
                            rulesList = rules.get(key);
                            longKey = key;
                        }
                    }
                }
            }
        }
        if (rulesList == null) {
            rulesList = new ArrayList<Rule>();
        }
        return (rulesList);
    }

    public void setClassLoader(ClassLoader cl) {
        classLoader = cl;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

}
