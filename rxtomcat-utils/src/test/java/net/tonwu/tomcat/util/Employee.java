package net.tonwu.tomcat.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Bean for Digester testing.
 */
public class Employee {

    private int age;
    private String firstName = null;
    private String lastName = null;
    
    private HashMap<String, String> attrs = new HashMap<>();
    
    private HashMap<String, List<String>> work = new HashMap<>();
    
    private Address addr;

    public void addWork(String name, String company) {
        List<String> companys = work.get(name);
        if (companys == null) {
            companys = new ArrayList<>();
            work.put(name, companys);
        }
        companys.add(company);
    }
    public HashMap<String, List<String>> getWork() {
        return work;
    }
    
    public int getAge() {
        return age;
    }
    public void setAge(int age) {
        this.age = age;
    }
    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    public void addAttr(String name, String value) {
        attrs.put(name, value);
    }
    public String getAttr(String key) {
        return attrs.get(key);
    }
    public Address getAddr() {
        return addr;
    }
    public void setAddr(Address addr) {
        this.addr = addr;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Employee[");
        sb.append("firstName=");
        sb.append(firstName);
        sb.append(", lastName=");
        sb.append(lastName);
        sb.append("]");
        return (sb.toString());
    }
}