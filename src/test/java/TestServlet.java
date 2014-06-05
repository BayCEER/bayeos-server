

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

public class TestServlet extends HttpServlet{
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		Context initCtx;
		try {
			System.out.println("Get");
			initCtx = new InitialContext();
			DataSource ds = (DataSource) initCtx.lookup("java:comp/env/jdbc/test");
			Connection con = ds.getConnection();
		
			PrintWriter pw = new PrintWriter(resp.getOutputStream());
			
			Statement st = con.createStatement();
			ResultSet rs = st.executeQuery("select * from test");
			
			while(rs.next()){
				pw.println(rs.getString(1));
			}
			st.close();
			rs.close();
			con.close();
			
			pw.flush();
			pw.close();
			
		} catch (NamingException | SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		
			
			
	}

}
