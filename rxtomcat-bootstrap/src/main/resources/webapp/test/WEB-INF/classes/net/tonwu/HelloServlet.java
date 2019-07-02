package net.tonwu;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * 
 * @author tonwu.net
 */
public class HelloServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    static Logger log = Logger.getLogger(HelloServlet.class);
    
    @Override
    public void init() throws ServletException {
        StringBuilder cl = new StringBuilder();  
        cl.append("==============ClassLoader=============="); 
        cl.append("\r\nCurrent Thread - ").append(Thread.currentThread().getContextClassLoader());
        cl.append("\r\nHelloServlet - ").append(getClass().getClassLoader());
        cl.append("\r\nLogger - ").append(log.getClass().getClassLoader());
        log.info(cl.toString());
        log.info("==============END==============");
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	    log.info("调用 Servlet - " + this);
	    String none = request.getParameter("none");
        String path = request.getRequestURI();
	    if (path.endsWith("error")) {
	        none.isEmpty(); // npe
	    }
	    
	    response.setContentType("text/html");
        response.setCharacterEncoding("utf-8");
        
        StringBuilder content = new StringBuilder();
        content.append("<pre>Server version: " + "RxTomcat/1.1" + "\r\n");
        content.append("OS Name:        " + System.getProperty("os.name") + "\r\n");
        content.append("OS Version:     " + System.getProperty("os.version") + "\r\n");
        content.append("Architecture:   " + System.getProperty("os.arch") + "\r\n");
        content.append("JVM Version:    " + System.getProperty("java.runtime.version") + "\r\n");
        content.append("JVM Vendor:     " + System.getProperty("java.vm.vendor") + "\r\n\r\n");
        content.append(request.toString()).append("\r\n");
        content.append(response.toString()).append("</pre>");
        
	    boolean userWriter = false;
	    StringBuilder sb = new StringBuilder();
	    sb.append("<html>");
	    sb.append("<head>");
	    String title = "";
	    String body = "";
	    
	    if (path.endsWith("stream")) {
	        title = "stream - Hello Servlet";
	        body = "<body><h3>Hello Servlet - Test getOutputStream()</h3>";
	    } else {
	        userWriter = true;
	        title = "writer - Hello Servlet";
            body = "<body><h3>Hello Servlet - Test getWriter()</h3>";
	    }
	    sb.append("<link rel=\"stylesheet\" type=\"text/css\" href=\""+request.getContextPath()+"/style.css\">");
	    sb.append("<title>").append(title).append("</title></head><body>");
	    sb.append(body).append(content.toString());
	    sb.append("<div><a href=\""+request.getContextPath()+"\">返回</a></div>");
	    sb.append("</body></html>");
	    
	    byte[] data = sb.toString().getBytes("utf-8");
	    
	    if (userWriter) {
	        // identity
	        response.setContentLength(data.length);
	        PrintWriter writer = response.getWriter();
	        writer.write(sb.toString());
	        writer.close();
	    } else {
	        // chunked
	        ServletOutputStream out = response.getOutputStream();
	        response.setHeader("Transfer-Encoding", "chunked");
	        out.write(data);
	        out.flush();
	    }
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}
}