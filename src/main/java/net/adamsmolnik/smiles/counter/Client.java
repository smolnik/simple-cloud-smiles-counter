package net.adamsmolnik.smiles.counter;

import static java.nio.file.Files.newInputStream;
import static java.nio.file.Paths.get;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Properties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * @author asmolnik
 *
 */
public class Client {

	private static char[] PASSWORD = "".toCharArray();

	private static String MESSAGE_TEMPLATE = "{\"serialNumber\": \"$serialNumber\", \"batteryVoltage\": \"-1\", \"clickType\": \"$clickType\"}";

	private static enum SmileType {
		LIKE("SINGLE"), LOVE("DOUBLE");

		private final String value;

		private SmileType(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

	}

	private static class CloudSmilesButtons extends JPanel {

		private static final long serialVersionUID = -2739162921203553230L;

		private CloudSmilesButtons(String serialNumber, String topic, MqttClient client) {
			super(new BorderLayout());
			JButton iLikeButton = new JButton("I Like It!");
			iLikeButton.setPreferredSize(new Dimension(200, 80));
			add(iLikeButton, BorderLayout.LINE_START);
			iLikeButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					try {
						sendCloudSmile(serialNumber, SmileType.LIKE, topic, client);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}

			});

			JButton iLoveButton = new JButton("I Love It!");
			iLoveButton.setPreferredSize(new Dimension(200, 80));

			add(iLoveButton, BorderLayout.LINE_END);
			iLoveButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					try {
						sendCloudSmile(serialNumber, SmileType.LOVE, topic, client);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}

			});

		}

	}

	private static JFrame createAndShowGUI(String serialNumber, String topic, MqttClient client) {
		JFrame frame = new JFrame("CloudSmilesButtons");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		JComponent newContentPane = new CloudSmilesButtons(serialNumber, topic, client);
		newContentPane.setOpaque(true);
		frame.setContentPane(newContentPane);
		frame.pack();
		frame.setVisible(true);
		return frame;
	}

	public static void main(String[] args) throws Exception {
		Properties p = new Properties();
		p.load(newInputStream(Paths.get("c://aws-iot/client.properties")));
		String topic = p.getProperty("topic");
		String broker = p.getProperty("broker");
		String clientId = p.getProperty("clientId");
		String deviceId = p.getProperty("deviceId");
		MemoryPersistence persistence = new MemoryPersistence();
		final MqttClient client = new MqttClient(broker, clientId, persistence);
		MqttConnectOptions connOpts = new MqttConnectOptions();
		connOpts.setSocketFactory(newInstance(p));
		client.connect(connOpts);

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				createAndShowGUI(deviceId, topic, client).addWindowListener(new WindowAdapter() {

					@Override
					public void windowClosed(WindowEvent e) {
						try {
							client.disconnect();
							client.close();
						} catch (MqttException e1) {
							e1.printStackTrace();
						}
					}
				});
				;
			}
		});
	}

	private static SSLSocketFactory newInstance(Properties p) throws Exception {
		X509Certificate ca = null;
		X509Certificate cert = null;
		KeyPair key = null;

		Security.addProvider(new BouncyCastleProvider());
		String keysDir = p.getProperty("keysDir");
		String deviceId = p.getProperty("deviceId");
		try (PEMReader caReader = new PEMReader(new InputStreamReader(newInputStream(get(keysDir, "ca.pem"))));
				PEMReader certReader = new PEMReader(new InputStreamReader(newInputStream(get(keysDir, deviceId + "-cert.pem.crt"))));
				PEMReader pkReader = new PEMReader(new InputStreamReader(newInputStream(get(keysDir, deviceId + "-private-key.pem"))),
						new PasswordFinder() {
							@Override
							public char[] getPassword() {
								return "".toCharArray();
							}
						});

		) {
			ca = (X509Certificate) caReader.readObject();
			cert = (X509Certificate) certReader.readObject();
			key = (KeyPair) pkReader.readObject();
		}
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
		caKs.load(null, null);
		caKs.setCertificateEntry("ca-iot", ca);
		tmf.init(caKs);
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		ks.load(null, null);
		ks.setCertificateEntry(deviceId + "-cert", cert);
		ks.setKeyEntry(deviceId + "-private-key", key.getPrivate(), PASSWORD, new java.security.cert.Certificate[] { cert });
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, PASSWORD);
		SSLContext context = SSLContext.getInstance("TLSv1.2");
		context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		return context.getSocketFactory();
	}

	private static void sendCloudSmile(String serialNumber, SmileType st, String topic, MqttClient client) throws Exception {
		MqttMessage message = new MqttMessage(
				MESSAGE_TEMPLATE.replace("$serialNumber", serialNumber).replace("$clickType", st.getValue()).getBytes());
		client.publish(topic, message);
	}

}
