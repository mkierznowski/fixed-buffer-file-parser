package pl.britenet.assembly;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.britenet.cutter.BufferCutter;
import pl.britenet.db.DBWriteable;
import pl.britenet.entity.Contact;
import pl.britenet.entity.Customer;
import pl.britenet.files.FixedBufferReader;
import pl.britenet.parsers.CSVParseable;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Component
public class CustomerCSVAssembler extends Assembler {

    private CSVParseable parseable;

    @Autowired
    public CustomerCSVAssembler(FixedBufferReader reader, DBWriteable dbWriteable, CSVParseable parseable) {
        super(reader, dbWriteable);
        this.parseable = parseable;
    }

    @Override
    public void parseFile(Path path, int buffer) throws IOException {
        createTables();
        Optional<String> stringOptional = Optional.of("");
        BufferCutter cutter = parseable.getCSVCutter();

        int customerIndex = 0;
        int contactIndex = 0;

        while (!EOF) {
            char[] readed = reader.readFixedBytes(path, buffer, readBuffer);
            String complete = cutter.getCompleteBuffer(stringOptional.get() + String.valueOf(readed));
            stringOptional = cutter.getPartialBuffer(String.valueOf(readed));
            List<Customer> customers = parseable.getCSVMapper().mapToObjects(complete);
            try {
                Connection conn;
                PreparedStatement st;
                conn = DriverManager.getConnection(Assembler.DATABASE_URL, Assembler.DATABASE_USER, Assembler.DATABASE_PASSWORD);
                for (Customer customer : customers) {
                    customer.setId(++customerIndex);
                    st = customer.getInsertSQL(conn);
                    st.execute();
                    for (String contact : customer.getContacts()) {
                        Contact c = new Contact();
                        c.setContact(contact);
                        c.setId(contactIndex++);
                        c.setId_customer(customerIndex);
                        st = c.getInsertSQL(conn);
                        st.execute();
                    }
                }
                conn.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            readBuffer += buffer;
            EOF = !stringOptional.isPresent();
        }
    }

    @Override
    public String getExtension() {
        return ".csv";
    }
}
