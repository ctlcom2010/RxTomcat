package net.tonwu;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;

public class SessionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    static Logger log = Logger.getLogger(SessionServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html");
        resp.setCharacterEncoding("utf-8");
        PrintWriter writer = resp.getWriter();
        writer.println("<html>");
        writer.println("<head>");
        writer.println("<title>Login</title></head>");
        writer.println("<link rel=\"stylesheet\" type=\"text/css\" href=\"" + req.getContextPath() + "/style.css\">");
        writer.println("<body><h1>Session 和 Cookie 的测试</h1>");

        HttpSession session = req.getSession(false);
        writer.print("<p>已登录用户：");
        writer.print(session.getAttribute("user"));
        writer.println("</p>");

        writer.print("<p>SessionID：");
        writer.print(session.getId());
        writer.println("</p>");

        writer.print("<p>请求 Cookie：</p>");
        writer.println("<ul>");

        Cookie[] cookies = req.getCookies();
        for (Cookie cookie : cookies) {
            writer.println("<li>");
            writer.println("Cookies: name=" + cookie.getName() + ", value=" + cookie.getValue());
            writer.println("</li>");
        }
        writer.println("</ul>");
        
        writer.println("<p><form method='post'><input type='submit' value='注销'/></form></p>");
        writer.println("</body>");
        writer.println("</html>");
    
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
            
            Cookie[] cookies = req.getCookies();
            for (Cookie cookie : cookies) {
                if ("user".equals(cookie.getName())) {
                    cookie.setMaxAge(-1);
                    resp.addCookie(cookie);
                }
            }
            
            resp.sendRedirect(req.getContextPath());
        }
    }
}
