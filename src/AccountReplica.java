import com.jcraft.jsch.*;
import org.ini4j.Ini;
import spread.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AccountReplica {

    static UUID id;
    static double balance;
    static List<Transaction> executedList;
    static List<Transaction> outstandingCollection;
    static int outstandingCounter;
    static int orderCounter;


    public static void setupTunnel (String host) throws IOException, JSchException {

        String username, password;
        Ini ini = null;

        File configFile = new File("conf.ini");
        try{
            ini = new Ini(configFile);
            username = ini.get("login","username");
            password = ini.get("login","password");
        }catch(IOException e){
            System.out.println("Enter uio username:");
            username = System.console().readLine();
            System.out.println("Enter password:");
            password = String.valueOf(System.console().readPassword());
        }

        int port = 22;
        int tunnelLocalPort = 4803;
        String tunnelRemoteHost = "127.0.0.255";
        int tunnelRemotePort = 4803;

        JSch jsch = new JSch();
        Session session = null;
        session = jsch.getSession(username, host, port);
        session.setPassword(password);
        LocalUserInfo lui = new LocalUserInfo();
        session.setUserInfo(lui);
        session.connect();
        session.setPortForwardingL(tunnelLocalPort,tunnelRemoteHost,tunnelRemotePort);

        if(ini == null){
            System.out.println("Save login (y/n)? :");
            String saveLogin = System.console().readLine();
            if (saveLogin.equals("y") || saveLogin.equals("yes")){
                if(configFile.createNewFile()) {
                    ini = new Ini(configFile);
                    ini.put("login", "username", username);
                    ini.put("login", "password", password);
                    ini.store();
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {

        id = UUID.randomUUID();
        balance = 0.0;
        executedList = new ArrayList<>();
        outstandingCollection = new ArrayList<>();

        SpreadConnection connection = new SpreadConnection();
        Listener listener = new Listener();

        try {
            if(args.length == 0 || args[0].equals("129.240.65.61"))
                setupTunnel("129.240.65.61");

            connection.add(listener);
            connection.connect(InetAddress.getByName(args.length == 0 || args[0].equals("129.240.65.61") ? "127.0.0.1" : args[0]), 4803, String.valueOf(id), false, true);

            SpreadGroup group = new SpreadGroup();
            group.join(connection, "G5");
            SpreadMessage[] messages = new SpreadMessage[2];

            Transaction t1 = new Transaction("deposit 10", id + "_" + orderCounter++);
            Transaction t2 = new Transaction("deposit 10", id + "_" + orderCounter++);

            messages[0] = new SpreadMessage();
            messages[1] = new SpreadMessage();

            messages[0].addGroup(group);
            messages[0].setFifo();
            messages[0].setObject(t1.toString());

            messages[1].addGroup(group);
            messages[1].setFifo();
            messages[1].setObject(t2.toString());

            connection.multicast(messages);

        } catch (SpreadException | IOException | JSchException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Hello world!");
        Thread.sleep(100000000);
    }

    public static void deposit(double amount){
        balance += amount;
    }

    public static void withdraw(double amount){
        balance -= amount;
    }

    public static void addInterest(double amount){
        balance += balance * amount * 0.01;
    }

}