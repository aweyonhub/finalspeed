// Copyright (c) 2015 D1SM.net
package net.fs.client;

import net.fs.rudp.ClientProcessorInterface;
import net.fs.rudp.ConnectionProcessor;
import net.fs.rudp.Route;
import net.fs.rudp.TrafficEvent;
import net.fs.rudp.Trafficlistener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Random;

public class MapClient implements Trafficlistener{

	ConnectionProcessor imTunnelProcessor;

	Route route_udp,route_tcp;

	short routePort=45;

	String serverAddress="";

	InetAddress address=null;

	int serverPort=130;

	long lastTrafficTime;

	int downloadSum=0;

	int uploadSum=0;

	HashSet<ClientProcessorInterface> processTable=new HashSet<ClientProcessorInterface>();

	Object syn_process=new Object();
	
	static MapClient mapClient;
	
	PortMapManager portMapManager;
		
	String systemName=System.getProperty("os.name").toLowerCase();
	
	boolean useTcp=true;
	
	MapClient(boolean tcpEnvSuccess) throws Exception {
		mapClient=this;
		try {
			route_tcp = new Route(null,routePort,Route.mode_client,true,tcpEnvSuccess);
		} catch (Exception e1) {
			//e1.printStackTrace();
			throw e1;
		}
		try {
			route_udp = new Route(null,routePort,Route.mode_client,false,tcpEnvSuccess);
		} catch (Exception e1) {
			//e1.printStackTrace();
			throw e1;
		}

		portMapManager=new PortMapManager(this);

		Route.addTrafficlistener(this);
		
	}
	
	public static MapClient get(){
		return mapClient;
	}

	public void setMapServer(String serverAddress,int serverPort,boolean tcp){
		if(this.serverAddress==null
				||!this.serverAddress.equals(serverAddress)
				||this.serverPort!=serverPort){
			
			if(route_tcp.lastClientControl!=null){
				route_tcp.lastClientControl.close();
			} 
			
			if(route_udp.lastClientControl!=null){
				route_udp.lastClientControl.close();
			} 

			cleanRule();
			if(serverAddress!=null&&!serverAddress.equals("")){
				setFireWallRule(serverAddress,serverPort);
			}
			
		}
		this.serverAddress=serverAddress;
		this.serverPort=serverPort;
		address=null;
		useTcp=tcp;
		resetConnection();
	}
	

	void setFireWallRule(String serverAddress,int serverPort){
		String ip;
		try {
			ip = InetAddress.getByName(serverAddress).getHostAddress();
			if(systemName.contains("linux")){
				String cmd2="iptables -t filter -A OUTPUT -d "+ip+" -p tcp --dport "+serverPort+" -j DROP -m comment --comment tcptun_fs ";
				runCommand(cmd2);
			}else if (systemName.contains("windows")) {
				try {
					if(systemName.contains("xp")||systemName.contains("2003")){
						String cmd_add1="ipseccmd -w REG -p \"tcptun_fs\" -r \"Block TCP/"+serverPort+"\" -f 0/255.255.255.255="+ip+"/255.255.255.255:"+serverPort+":tcp -n BLOCK -x ";
						final Process p2 = Runtime.getRuntime().exec(cmd_add1,null);
						p2.waitFor();
					}else {
						String cmd_add1="netsh advfirewall firewall add rule name=tcptun_fs protocol=TCP dir=out remoteport="+serverPort+" remoteip="+ip+" action=block ";
						final Process p2 = Runtime.getRuntime().exec(cmd_add1,null);
						p2.waitFor();
						String cmd_add2="netsh advfirewall firewall add rule name=tcptun_fs protocol=TCP dir=in remoteport="+serverPort+" remoteip="+ip+" action=block ";
						Process p3 = Runtime.getRuntime().exec(cmd_add2,null);
						p3.waitFor();
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	void cleanRule(){
		if(systemName.contains("mac os")){
			cleanTcpTunRule_osx();
		}else if(systemName.contains("linux")){
			cleanTcpTunRule_linux();
		}else {
			try {
				if(systemName.contains("xp")||systemName.contains("2003")){
					String cmd_delete="ipseccmd -p \"tcptun_fs\" -w reg -y";
					final Process p1 = Runtime.getRuntime().exec(cmd_delete,null);
					p1.waitFor();
				}else {
					String cmd_delete="netsh advfirewall firewall delete rule name=tcptun_fs ";
					final Process p1 = Runtime.getRuntime().exec(cmd_delete,null);
					p1.waitFor();
				}
			
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	void cleanTcpTunRule_osx(){
		String cmd2="sudo ipfw delete 5050";
		runCommand(cmd2);
	}
	
	
	void cleanTcpTunRule_linux(){
		while(true){
			int row=getRow_linux();
			if(row>0){
				//System.out.println("删除行 "+row);
				String cmd="iptables -D OUTPUT "+row;
				runCommand(cmd);
			}else {
				break;
			}
		}
	}

	int getRow_linux(){
		int row_delect=-1;
		String cme_list_rule="iptables -L -n --line-number";
		//String [] cmd={"netsh","advfirewall set allprofiles state on"};
		Thread errorReadThread=null;
		try {
			final Process p = Runtime.getRuntime().exec(cme_list_rule,null);

			errorReadThread=new Thread(){
				public void run(){
					InputStream is=p.getErrorStream();
					BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
					while (true){
						String line; 
						try {
							line = localBufferedReader.readLine();
							if (line == null){ 
								break;
							}else{ 
								//System.out.println("erroraaa "+line);
							}
						} catch (IOException e) {
							e.printStackTrace();
							//error();
							break;
						}
					}
				}
			};
			errorReadThread.start();



			InputStream is=p.getInputStream();
			BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
			while (true){
				String line; 
				try {
					line = localBufferedReader.readLine();
				//	System.out.println("standaaa "+line);
					if (line == null){ 
						break;
					}else{ 
						if(line.contains("tcptun_fs")){
							int index=line.indexOf("   ");
							if(index>0){
								String n=line.substring(0, index);
								try {
									if(row_delect<0){
										//System.out.println("standaaabbb "+line);
										row_delect=Integer.parseInt(n);
									}
								} catch (Exception e) {

								}
							}
						};
					}
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
			}


			errorReadThread.join();
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
			//error();
		}
		return row_delect;
	}
	
	void resetConnection(){
		synchronized (syn_process) {
			
		}
	}
	
	public void onProcessClose(ClientProcessorInterface process){
		synchronized (syn_process) {
			processTable.remove(process);
		}
	}

	synchronized public void closeAndTryConnect_Login(boolean testSpeed){
		close();
		/*boolean loginOK=ui.login();
		if(loginOK){
			ui.updateNode(testSpeed);
			//testPool();
		}*/
	}

	synchronized public void closeAndTryConnect(){
		close();
		//testPool();
	}

	public void close(){
		//closeAllProxyRequest();
		//poolManage.close();
		//CSocketPool.closeAll();
	}
	
	public void trafficDownload(TrafficEvent event) {
		////#System.out.println("下载 "+event.getTraffic());
		lastTrafficTime=System.currentTimeMillis();
		downloadSum+=event.getTraffic();
	}

	public void trafficUpload(TrafficEvent event) {
		////#System.out.println("上传 "+event.getTraffic());
		lastTrafficTime=System.currentTimeMillis();
		uploadSum+=event.getTraffic();
	}

	static void runCommand(String command){
		Thread standReadThread=null;
		Thread errorReadThread=null;
		try {
			final Process p = Runtime.getRuntime().exec(command,null);
			standReadThread=new Thread(){
				public void run(){
					InputStream is=p.getInputStream();
					BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
					while (true){
						String line; 
						try {
							line = localBufferedReader.readLine();
							//System.out.println("stand "+line);
							if (line == null){ 
								break;
							}
						} catch (IOException e) {
							e.printStackTrace();
							break;
						}
					}
				}
			};
			standReadThread.start();

			errorReadThread=new Thread(){
				public void run(){
					InputStream is=p.getErrorStream();
					BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
					while (true){
						String line; 
						try {
							line = localBufferedReader.readLine();
							if (line == null){ 
								break;
							}else{ 
								//System.out.println("error "+line);
							}
						} catch (IOException e) {
							e.printStackTrace();
							//error();
							break;
						}
					}
				}
			};
			errorReadThread.start();
			standReadThread.join();
			errorReadThread.join();
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
			//error();
		}
	}

	public boolean isUseTcp() {
		return useTcp;
	}

	public void setUseTcp(boolean useTcp) {
		this.useTcp = useTcp;
	}


}
