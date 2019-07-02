package net.tonwu.tomcat.util;

public class Address {

    private String type;
    private String street;
    private String city;
    
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getStreet() {
        return street;
    }
    public void setStreet(String street) {
        this.street = street;
    }
    public String getCity() {
        return city;
    }
    public void setCity(String city) {
        this.city = city;
    }
    
    @Override
    public String toString() {
        return "Address [type=" + type + ", street=" + street + ", city=" + city + "]";
    }
}
