package net.tonwu.tomcat.container.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Properties;
import java.util.Scanner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import net.tonwu.tomcat.container.Valve;
import net.tonwu.tomcat.container.servletx.Request;
import net.tonwu.tomcat.container.servletx.Response;
import net.tonwu.tomcat.util.Escape;

public class ErrorReportValve extends Valve {
    
    public static final String TOMCAT_CSS =
            "h1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;} " +
            "h2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} " +
            "h3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} " +
            "body {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;} " +
            "b {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;} " +
            "p {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;} " +
            "a {color:black;} " +
            "a.name {color:black;} " +
            ".line {height:1px;background-color:#525D76;border:none;}";
    
    private Properties httpCodeReasons = new Properties();
    
    public ErrorReportValve() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        System.out.println(cl);
        try (InputStream is = cl.getResourceAsStream("http-code-reason.properties");) {
            httpCodeReasons.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
    
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        // Perform the request
        getNext().invoke(request, response);

        if (response.isCommitted()) {
            return;
        }

        Throwable throwable = (Throwable) request.getAttribute("javax.servlet.exception");

        if (throwable != null) {
            // 清空已经写入缓冲区的响应体数据，此时还没有 committed
            response.reset();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        // 设置要发送响应体，因为 sendError 设置不返回响应体数据
        response.setSuspended(false);

        try {
            report(request, response, throwable);
        } catch (Throwable tt) { } // ignore
    }

    /**
     * Prints out an error report.
     *
     * @param request   The request being processed
     * @param response  The response being generated
     * @param throwable The exception that occurred (which possibly wraps a root cause exception
     */
    protected void report(Request request, Response response, Throwable throwable) {

        int statusCode = response.getStatus();

        // Do nothing on a 1xx, 2xx and 3xx status
        // Do nothing if the response hasn't been explicitly marked as in error
        // and that error has not been reported.
        if (statusCode < 400 || !response.isError()) {
            return;
        }

        String message = Escape.htmlElementContent(response.getMessage());
        if (message == null) {
            if (throwable != null) {
                String exceptionMessage = throwable.getMessage();
                if (exceptionMessage != null && exceptionMessage.length() > 0) {
                    message = Escape.htmlElementContent((new Scanner(exceptionMessage)).nextLine());
                }
            }
            if (message == null) {
                message = "";
            }
        }

     // Do nothing if there is no report for the specified status code
        String reason = null;
        String description = null;
        try {
            reason = httpCodeReasons.getProperty("http." + statusCode + ".reason");
            description = httpCodeReasons.getProperty("http." + statusCode + ".desc");
        } catch (Throwable t) { }
        if (reason == null)
            return;
        StringBuffer sb = new StringBuffer();
        
        sb.append("<html><head><title>");
        sb.append("RxTomcat - Error report");
        sb.append("</title>");
        sb.append("<style type=\"text/css\">");
        sb.append(TOMCAT_CSS);
        sb.append("</style>");
        sb.append("</head><body>");
        sb.append("<h1>");
        sb.append("HTTP Status ").append(statusCode).append(" - ").append(reason).append("</h1>");
        sb.append("<hr class=\"line\" />");
        sb.append("<p><b>Type</b> ");
        if (throwable != null) {
            sb.append("Exception Report");
        } else {
            sb.append("Status Report");
        }
        sb.append("</p>");
        if (!message.isEmpty()) {
            sb.append("<p><b>Message</b> ");
            sb.append(message).append("</p>");
        }
        sb.append("<p><b>Description</b> ");
        sb.append(description);
        sb.append("</p>");
        
        if (throwable != null) {
            String stackTrace = getPartialServletStackTrace(throwable);
            sb.append("<p><b>Exception</b></p><pre>");
            sb.append(Escape.htmlElementContent(stackTrace));
            sb.append("</pre>");

            int loops = 0;
            Throwable rootCause = throwable.getCause();
            while (rootCause != null && (loops < 10)) {
                stackTrace = getPartialServletStackTrace(rootCause);
                sb.append("<p><b>Root Cause</b></p><pre>");
                sb.append(Escape.htmlElementContent(stackTrace));
                sb.append("</pre>");
                // In case root cause is somehow heavily nested
                rootCause = rootCause.getCause();
                loops++;
            }

            sb.append("<p><b>Note</b> ");
            sb.append("The full stack trace of the root cause is available in the server logs.");
            sb.append("</p>");
        }
        
        sb.append("<hr class=\"line\" />");
        sb.append("<h3>RxTomcat</h3>");
        sb.append("</body></html>");
        
        try {
            try {
                response.setContentType("text/html");
                response.setCharacterEncoding("utf-8");
            } catch (Throwable t) {
                container.log("status.setContentType" + t.getMessage());
            }
            Writer writer = response.getReporter();
            if (writer != null) {
                // If writer is null, it's an indication that the response has
                // been hard committed already, which should never happen
                writer.write(sb.toString());
                response.finish();
            }
        } catch (IOException e) {
            // Ignore
        } catch (IllegalStateException e) {
            // Ignore
        }
    }

    /**
     * Print out a partial servlet stack trace (truncating at the last
     * occurrence of javax.servlet.).
     * 
     * @param t
     *            The stack trace to process
     * @return the stack trace relative to the application layer
     */
    protected String getPartialServletStackTrace(Throwable t) {
        StringBuilder trace = new StringBuilder();
        trace.append(t.toString()).append(System.lineSeparator());
        StackTraceElement[] elements = t.getStackTrace();
        int pos = elements.length;
        for (int i = elements.length - 1; i >= 0; i--) { // 找到容器内部堆栈开始位置
            if ((elements[i].getClassName().startsWith("net.tonwu.tomcat.container.servletx.AppFilterChain"))
                    && (elements[i].getMethodName().equals("doFilter"))) {
                pos = i;
                break;
            }
        }
        for (int i = 0; i < pos; i++) { // 排除容器内部堆栈信息
            if (!(elements[i].getClassName().startsWith("net.tonwu.tomcat.container."))) {
                trace.append('\t').append(elements[i].toString()).append(System.lineSeparator());
            }
        }
        return trace.toString();
    }
}
