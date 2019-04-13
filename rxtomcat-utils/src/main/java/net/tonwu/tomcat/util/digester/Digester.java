package net.tonwu.tomcat.util.digester;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import net.tonwu.tomcat.util.digester.rule.ObjectCreateRule;
import net.tonwu.tomcat.util.digester.rule.SetFieldsRule;
import net.tonwu.tomcat.util.digester.rule.SetObjectFieldRule;
import net.tonwu.tomcat.util.log.Log;
import net.tonwu.tomcat.util.log.LoggerFactory;

/**
 * 依据配置好的规则使用 Sax 解析 XML，只对节点名和属性处理，不处理内容
 * 
 * @author wskwbog
 */
public class Digester extends DefaultHandler {
    final static Log log = LoggerFactory.getLogger(Digester.class);

    private Object root;

    /** 对象栈 */
    private Deque<Object> stack = new ArrayDeque<>();

    /** 解析过程中节点对应匹配的规则 */
    public Deque<List<Rule>> matches = new ArrayDeque<>();

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

            reader.setContentHandler(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return reader;
    }

    // Sax method
    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        StringBuilder sb = new StringBuilder(match);
        if (match.length() > 0) {
            sb.append('/');
        }
        sb.append(qName);
        match = sb.toString();

        log.debug("New match: {}", match);

        List<Rule> matchRules = matchRules(match);
        matches.push(matchRules);

        for (int i = 0; i < matchRules.size(); i++) {
            try {
                Rule rule = matchRules.get(i);
                log.debug("Fire begin() for {}", rule);

                rule.begin(uri, qName, attributes);
            } catch (Exception e) {
                throw new SAXException(e);
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        List<Rule> matchRules = matches.pop();
        for (int i = 0; i < matchRules.size(); i++) { // 倒叙遍历
            int j = (matchRules.size() - 1) - i;
            try {
                Rule rule = matchRules.get(j);
                log.debug("Fire end() for {}", rule);

                rule.end(uri, qName);
            } catch (Exception e) {
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

    public void addSetObjectField(String pattern, String methodName) {
        SetObjectFieldRule setObjectFieldRule = new SetObjectFieldRule(methodName);
        setObjectFieldRule.setDigester(this);
        addRule(pattern, setObjectFieldRule);
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

}
