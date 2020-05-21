import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;

public class UDPLoggerClient
{
	private final int loggerServerPort;
	private final int processId;
	private final int timeout;
	private final DatagramSocket socket;
	private final ExecutorService logService = Executors.newSingleThreadExecutor();

	/**
	 * @param loggerServerPort the UDP port where the Logger process is listening o
	 * @param processId the ID of the Participant/Coordinator, i.e. the TCP port where the Participant/Coordinator is listening on
	 * @param timeout the timeout in milliseconds for this process 
	 */
	public UDPLoggerClient(int loggerServerPort, int processId, int timeout) {
		this.loggerServerPort = loggerServerPort;
		this.processId = processId;
		this.timeout = timeout;
		this.socket = initialise();
	}
	
	public int getLoggerServerPort() {
		return loggerServerPort;
	}

	public int getProcessId() {
		return processId;
	}
	
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Sends a log message to the Logger process
	 * 
	 * @param message the log message
	 * @throws IOException
	 */
	public void logToServer(String message) throws IOException
	{
		Runnable logToServer = () -> {
			ExecutorService logServiceModular = Executors.newSingleThreadExecutor();

			Runnable logMessage = () -> {
				byte[] buffer = message.getBytes();

				InetAddress address = null;

				try
				{
					address = InetAddress.getLocalHost();
				}
				catch (UnknownHostException e)
				{
					e.printStackTrace();
				}

				DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, loggerServerPort);

				try
				{
					socket.send(packet);
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			};

			Callable<Boolean> acknowledgement = () -> {
				String ack = "ACK";

				DatagramPacket packet = new DatagramPacket(ack.getBytes(), ack.getBytes().length);

				socket.receive(packet);

				return new String(packet.getData()).equals(ack);
			};

			boolean acknowledgementReceived = false;
			int i = 0;

			while (!acknowledgementReceived && i < 3)
			{
				logServiceModular.execute(logMessage);

				Future<Boolean> futureReceive = logServiceModular.submit(acknowledgement);

				try
				{
					acknowledgementReceived = futureReceive.get(timeout, TimeUnit.MILLISECONDS);
				}
				catch (TimeoutException ignored)
				{
					i++;
				}
				catch (InterruptedException | ExecutionException ex)
				{
					ex.printStackTrace();
				}
			}

			logServiceModular.shutdown();
		};

		logService.execute(logToServer);
	}

	public DatagramSocket initialise()
	{
		try
		{
			return new DatagramSocket();
		}
		catch (SocketException ex)
		{
			ex.printStackTrace();
			return null;
		}
	}
}
