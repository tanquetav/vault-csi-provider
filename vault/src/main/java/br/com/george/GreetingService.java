package br.com.george;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@ApplicationScoped
public class GreetingService {
    @Inject
    DataSource defaultDataSource;

    public String getInfo() {
        try ( Connection conn = defaultDataSource.getConnection();
              PreparedStatement prep = conn.prepareStatement("SELECT concat(session_user, '   ',  current_user)") ;
              ResultSet rs = prep.executeQuery() ) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return "getinfo";
    }
}