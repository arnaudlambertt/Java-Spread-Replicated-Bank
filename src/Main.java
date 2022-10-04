import com.jcraft.jsch.*;
import spread.*;
import javax.swing.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
//import java.net.http.WebSocket;
import java.util.Random;



public class Main {

    public static void main(String[] args) throws InterruptedException {
        SpreadConnection connection = new SpreadConnection();
        Listener listener = new Listener();

        try {

            String host="129.240.65.59";
            String user="username";
            String password="password";
            int port=22;

            int tunnelLocalPort=4803;
            String tunnelRemoteHost="127.0.0.255";
            int tunnelRemotePort=4803;

            JSch jsch=new JSch();
            Session session= null;
            try {
                session = jsch.getSession(user, host, port);
            } catch (JSchException e) {
                throw new RuntimeException(e);
            }
            session.setPassword(password);
            LocalUserInfo lui = new LocalUserInfo();
            session.setUserInfo(lui);
            session.connect();
            session.setPortForwardingL(tunnelLocalPort,tunnelRemoteHost,tunnelRemotePort);
            System.out.println("Connected");

            connection.add(listener);

            // if the ifi machine is used <use the ifi machine ip address>
            //connection.connect(InetAddress.getByName("129.240.65.59"), 4803, "test connection", false, true);

            // for the local machine (172.18.0.1 is the loopback address in this machine)
            connection.connect(InetAddress.getByName("129.240.65.59"), 4803, "Arnaud", false, true);

            SpreadGroup group = new SpreadGroup();
            group.join(connection, "G5");
            SpreadMessage[] messages = new SpreadMessage[2];

            messages[0] = new SpreadMessage();
            messages[1] = new SpreadMessage();

            messages[0].addGroup(group);
            messages[0].setFifo();
            messages[0].setObject("Arnaud 1");

            messages[1].addGroup(group);
            messages[1].setFifo();
            messages[1].setObject("Arnaud 2");

            connection.multicast(messages);

        } catch (SpreadException e) {
            throw new RuntimeException(e);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (JSchException e) {
            throw new RuntimeException(e);
        }
        
        System.out.println("Hello world!");
        Thread.sleep(100000000);
    }

}