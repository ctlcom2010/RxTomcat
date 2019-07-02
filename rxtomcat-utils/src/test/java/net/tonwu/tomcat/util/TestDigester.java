package net.tonwu.tomcat.util;

import java.io.IOException;
import java.io.InputStream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import net.tonwu.tomcat.util.digester.Digester;

public class TestDigester {

    private Digester digester;

    @Before
    public void setUp() {
        digester = new Digester();
        
        // 初始化解析规则
        digester.addObjectCreate("employee", "net.tonwu.tomcat.util.Employee");
        digester.addSetFields("employee");

        digester.addCallMethod("employee/last-name", "setLastName", 0);
        digester.addCallMethod("employee/age", "setAge", 0, new Class[]{Integer.TYPE});
        
        digester.addObjectCreate("employee/address", "net.tonwu.tomcat.util.Address");
        digester.addSetFields("employee/address");
        digester.addSetNext("employee/address", "setAddr");
        
        digester.addCallMethod("employee/attr", "addAttr", 2);
        digester.addCallParam("employee/attr/key", 0);
        digester.addCallParam("employee/attr/value", 1);
        
        digester.addCallMethodMultiRule("employee/work", "addWork", 2, 1);
        digester.addCallParam("employee/work/name", 0);
        digester.addCallParamMultiRule("employee/work/company", 1);
    }

    @Test
    public void parse() throws IOException, SAXException {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("employee.xml");
        Employee employee = (Employee) digester.parse(new InputSource(is));
        Assert.assertTrue(digester.peek() == null); // 内部对象栈已经为空
        
        Assert.assertNotNull(employee);
        Assert.assertTrue("First Name".equals(employee.getFirstName()));
        Assert.assertTrue("Last Name".equals(employee.getLastName()));
        Assert.assertTrue(10 == employee.getAge());
        
        Assert.assertNotNull(employee.getAddr());
        Assert.assertTrue("home".equals(employee.getAddr().getType()));
        Assert.assertEquals("1",employee.getAttr("sex"));
        
        Assert.assertNotNull(employee.getWork());
        Assert.assertTrue(employee.getWork().get("work-1").contains("work-1-company-1"));
    }
    
    @After
    public void tearDown() {
        digester = null;
    }
}
