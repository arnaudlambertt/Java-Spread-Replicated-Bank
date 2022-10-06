import com.jcraft.jsch.*;
import org.ini4j.Ini;
import spread.*;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.rmi.MarshalledObject;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AccountReplica {

    static UUID id;
    static String accountName;
    static String serverAddress;
    static int numberOfReplicas;
    static SpreadGroup group;
    static SpreadConnection connection;
    static double balance;
    static List<Transaction> executedList;
    static final List<Transaction> outstandingCollection = Collections.synchronizedList(new ArrayList<>());
    static int outstandingCounter;
    static int orderCounter;
    static SpreadGroup[] membersInfo;
    static File file;

    public static void outstandingCollectionDaemon(){

        while(true) {
            List<SpreadMessage> messages = new ArrayList<>();

            for (Transaction t : outstandingCollection){
                SpreadMessage message = new SpreadMessage();
                message.addGroup(group);
                message.setFifo();
                try {
                    message.setObject(t.toString());
                } catch (SpreadException e) {
                    throw new RuntimeException(e);
                }
                messages.add(message);
            }
            try {
                connection.multicast(messages.toArray(new SpreadMessage[messages.size()]));
            } catch (SpreadException e) {
                throw new RuntimeException(e);
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

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

    public static String getQuickBalance(){
        return String.valueOf(balance);
    }

    public static String getSyncedBalance(){
        synchronized (outstandingCollection){
            try {
                if(!outstandingCollection.isEmpty())
                    outstandingCollection.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return String.valueOf(balance);
    }

    public static void getHistory(){
        for(Transaction t : executedList){
            System.out.println(t);
        }
    }

    public static void cleanHistory(){
        executedList.clear();
    }

    public static void main(String[] args) throws InterruptedException, NoSuchMethodException {

        if(args.length < 3)
            throw new IllegalArgumentException();

        serverAddress = args[0];
        accountName = args[1];
        numberOfReplicas = Integer.parseInt(args[2]);
        if(args.length > 3)
            file = new File(args[3]);

        id = UUID.randomUUID();
        balance = 0.0;
        executedList = new ArrayList<>();

        connection = new SpreadConnection();
        Listener listener = new Listener();

        try {
            if(serverAddress.equals("129.240.65.61"))
                setupTunnel(serverAddress);

            connection.add(listener);
            connection.connect(InetAddress.getByName(serverAddress.equals("129.240.65.61") ? "127.0.0.1" : serverAddress), 4803, String.valueOf(id), false, true);

            group = new SpreadGroup();
            group.join(connection, accountName);

            new Thread(AccountReplica::outstandingCollectionDaemon).start();

            synchronized (group){
                group.wait();
            }

            Transaction t1 = new Transaction("deposit 10", id + "_" + outstandingCounter++);
            Transaction t2 = new Transaction("deposit 20", id + "_" + outstandingCounter++);

            outstandingCollection.add(t1);
            outstandingCollection.add(t2);

        } catch (SpreadException | IOException | JSchException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodException(e.getMessage());
        }
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