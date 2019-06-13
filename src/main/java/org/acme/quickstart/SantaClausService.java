package org.acme.quickstart;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


@ApplicationScoped
public class SantaClausService {

    private static final Logger log = Logger.getLogger(SantaClausService.class.getName());


    @Inject
    AgroalDataSource defaultDataSource;

    @Inject
    @DataSource("dynamic")
    AgroalDataSource dynamicDataSource;

    @Transactional
    public void createGift(String giftDescription, String ds) {
        try (Connection c = getDatasource(ds).getConnection()) {
            try (PreparedStatement preparedStatement = c.prepareStatement("insert into gift (name) values (?)")) {
                preparedStatement.setString(1, giftDescription);
                int count = preparedStatement.executeUpdate();
                if (count != 1) {
                    throw new RuntimeException("count == " + count + "; should be 1");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> findAll(String ds) {
        List<String> gifts = new ArrayList<>();
        try (Connection c = getDatasource(ds).getConnection()) {
            try (PreparedStatement preparedStatement = c.prepareStatement("select * from gift")) {
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    while (rs.next()) {
                        gifts.add(rs.getLong("id") + ":" + rs.getString("name"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return gifts;
    }

    private AgroalDataSource getDatasource(String dsname) {
        return dsname == null ? defaultDataSource : dynamicDataSource;
    }


}
