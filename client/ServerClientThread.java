import java.io.*;
import java.net.*;
import java.util.Date;
class ServerClientThread extends Thread {
	  Socket serverClient;
	  int clientNo;
	  int squre;
	  ServerClientThread(Socket inSocket,int counter){
	    serverClient = inSocket;
	    clientNo=counter;
	  }
	  public void run(){
	    try{
	    	   DataOutputStream out=new DataOutputStream(serverClient.getOutputStream());
	        out.writeBytes("Server Date: " + (new Date()).toString() + "\n");
	      out.close();
	      serverClient.close();
	    }catch(Exception ex){
	      System.out.println(ex);
	    }finally{
	      System.out.println("Client -" + clientNo + " exit!! ");
	    }
	  }
	}
