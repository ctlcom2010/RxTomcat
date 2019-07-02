package net.tonwu;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    static Logger log = Logger.getLogger(LoginServlet.class);    
    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html");
        resp.setCharacterEncoding("utf-8");
        PrintWriter writer = resp.getWriter();
        writer.println("<html>");
        writer.println("<head>");
        writer.println("<title>Login</title></head>");
        writer.println("<link rel=\"stylesheet\" type=\"text/css\" href=\"" + req.getContextPath() + "/style.css\">");
        writer.println("<body><h1>Login</h1>");
        writer.println("<form method='post'>");
        writer.println("<table>");

        writer.println("<tr>");
        writer.println("<td>用户名:</td>");
        writer.println("<td><input name='username'/></td>");
        writer.println("</tr>");

        writer.println("<tr>");
        writer.println("<td>密码:</td>");
        writer.println("<td><input name='password'/></td>");
        writer.println("</tr>");

        writer.println("<tr>");
        writer.println("<td>&nbsp;</td>");
        writer.println("<td><input type='reset'/>" + "<input type='submit'/></td>");
        writer.println("</tr>");

        writer.println("</table>");
        writer.println("</form>");
        writer.println("</body>");
        writer.println("</html>");
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String user = req.getParameter("username");
        String passwd = req.getParameter("password");
        log.info(user + ", " + passwd);

        req.getSession().setAttribute("user", user);

        Cookie loginCookie = new Cookie("user", user);
        loginCookie.setMaxAge(1 * 60);
        resp.addCookie(loginCookie);
        
        resp.sendRedirect(req.getContextPath() + "/session");
    }
}
