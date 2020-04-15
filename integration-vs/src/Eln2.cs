using Vintagestory.API.Client;
using Vintagestory.API.Common;
using Vintagestory.API.Server;
using System;
using System.Net;
using System.Net.Sockets;
using System.Text;

[assembly: ModInfo( "Eln2",
	Description = "Electricity in your base!",
	Website     = "https://eln2.org",
	Authors     = new []{ "jrddunbr" } )]

namespace org.Eln2 {
	public class Eln2 : ModSystem {
		public override void Start(ICoreAPI api) {
			Console.WriteLine("Hello World! This is Regular Start!");

			Console.WriteLine(serverQuery());
		}
		
		public override void StartClientSide(ICoreClientAPI api) {
			Console.WriteLine("Hello World! This is Client Start!");
		}
		
		public override void StartServerSide(ICoreServerAPI api) {
			Console.WriteLine("Hello World! This is Server Start!");
		}

		private string serverQuery() {

			string request = "GET / HTTP/1.1\nHost: ja13.org \nConnection: Close\n\n";
        	Byte[] bytesSent = Encoding.ASCII.GetBytes(request);
        	Byte[] bytesReceived = new Byte[256];
        	string page = "";

			Byte[] ip = {71,88,54,229};
			IPEndPoint remote = new IPEndPoint(new IPAddress(ip), 80);
			using (Socket socket = new Socket(remote.AddressFamily, SocketType.Stream, ProtocolType.Tcp)) {

				socket.Connect(remote);
				Console.WriteLine("Socket bound: " + socket.IsBound);

				// Send request to the server.
				socket.Send(bytesSent, bytesSent.Length, 0);  
				
				// Receive the content.
				int bytes = 0;
				page = "";

				// The following will block until the data is transmitted.
				do {
					bytes = socket.Receive(bytesReceived, bytesReceived.Length, 0);
					page = page + Encoding.ASCII.GetString(bytesReceived, 0, bytes);
				}
				while (bytes > 0);

				return page;
			}
		}
	}
}
