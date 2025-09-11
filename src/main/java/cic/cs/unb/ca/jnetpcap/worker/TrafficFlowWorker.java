package cic.cs.unb.ca.jnetpcap.worker;

import cic.cs.unb.ca.jnetpcap.BasicFlow;
import cic.cs.unb.ca.jnetpcap.FlowGenerator;
import cic.cs.unb.ca.jnetpcap.PacketReader;
import cic.cs.unb.ca.notify.HttpClientUtil;
import cic.cs.unb.ca.python.PythonProcessManager;
import org.jnetpcap.Pcap;
import org.jnetpcap.nio.JMemory.Type;
import org.jnetpcap.packet.PcapPacket;
import org.jnetpcap.packet.PcapPacketHandler;
import org.jnetpcap.protocol.network.Ip4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.List;

public class TrafficFlowWorker extends SwingWorker<String,String> implements FlowGenListener{

	public static final Logger logger = LoggerFactory.getLogger(TrafficFlowWorker.class);
    public static final String PROPERTY_FLOW = "flow";
	private String device;
	private boolean stopped;
	private String notifyUrl;


    public TrafficFlowWorker(String device) {
		super();
		this.device = device;
		this.notifyUrl = null;
		this.stopped = false;

		PythonProcessManager.getInstance().subscribe(this::onFlowEvaluated);
	}

	public TrafficFlowWorker(String device, String notifyUrl) {
		this(device);
		if (notifyUrl != null && !notifyUrl.isEmpty()) {
			this.notifyUrl = notifyUrl;
		}
	}

	@Override
	protected String doInBackground() {
		
		FlowGenerator   flowGen = new FlowGenerator(true,120000000L, 5000000L);
		flowGen.addFlowListener(this);
		int snaplen = 64 * 1024;//2048; // Truncate packet at this size
		int promiscous = Pcap.MODE_PROMISCUOUS;
		int timeout = 60 * 1000; // In milliseconds
		StringBuilder errbuf = new StringBuilder();
		Pcap pcap = Pcap.openLive(device, snaplen, promiscous, timeout, errbuf);
		if (pcap == null) {
			logger.info("open {} fail -> {}",device,errbuf.toString());
			return String.format("open %s fail ->",device)+errbuf.toString();
		}

		PcapPacketHandler<String> jpacketHandler = (packet, user) -> {

            /*
             * BufferUnderflowException while decoding header
             * that is because:
             * 1.PCAP library is not multi-threaded
             * 2.jNetPcap library is not multi-threaded
             * 3.Care must be taken how packets or the data they referenced is used in multi-threaded environment
             *
             * typical rule:
             * make new packet objects and perform deep copies of the data in PCAP buffers they point to
             *
             * but it seems not work
             */
//			System.out.println("nuevo paquete");

			Ip4 ip = new Ip4();
			PcapPacket permanent = new PcapPacket(Type.POINTER);
			packet.transferStateAndDataTo(permanent);
//			System.out.println("paquete copiado");

			// 2) Comprueba si este paquete tiene cabecera IP
			/*if (permanent.hasHeader(ip)) {
				byte[] srcBytes = ip.source();
				byte[] dstBytes = ip.destination();
				try {
					String srcIp = InetAddress.getByAddress(srcBytes).getHostAddress();
					String dstIp = InetAddress.getByAddress(dstBytes).getHostAddress();

					logger.info("Paquete de {} a {}  – longitud {} bytes",
							srcIp,
							dstIp,
							permanent.getCaptureHeader().wirelen());

				} catch (UnknownHostException e) {
					logger.warn("No se pudo parsear IP", e);
				}
			} else {
				logger.debug("Paquete sin cabecera IPv4, longitud {}", packet.size());
			}//*/

			//logger.info("Paquete recibido");

            flowGen.addPacket(PacketReader.getBasicPacketInfo(permanent, true, false));
//			System.out.println("nose");

            if(isCancelled()) {
                pcap.breakloop();
                logger.debug("break Packet loop");
            }
        };

        //FlowMgr.getInstance().setListenFlag(true);
        logger.info("Pcap is listening...");
        firePropertyChange("progress","open successfully","listening: "+device);
        int ret = pcap.loop(Pcap.DISPATCH_BUFFER_FULL, jpacketHandler, device);

		String str;
        switch (ret) {
            case 0:
                str = "listening: " + device + " finished";
                break;
            case -1:
                str = "listening: " + device + " error";
                break;
            case -2:
                str = "stop listening: " + device;
                break;
                default:
                    str = String.valueOf(ret);
        }

        return str;
	}

	@Override
	protected void process(List<String> chunks) {
		super.process(chunks);
	}

	@Override
	protected void done() {
		super.done();
	}

	@Override
	public void onFlowGenerated(BasicFlow flow) {
        firePropertyChange(PROPERTY_FLOW,null,flow);
		if (this.stopped) return;
		logger.info("Enviando Mensaje");
		PythonProcessManager.getInstance().sendMsg(flow.dumpFlowBasedFeaturesEx());
	}

	public void onFlowEvaluated(String result) {
		if (result != null && !result.isEmpty()) {
			logger.info("Flow evaluation result: {}", result);
			firePropertyChange("flowEvaluation", null, result);
			if (!result.equals("BENIGN")) {
				logger.warn("Flow evaluation result indicates potential threat: {}", result);
				this.stopped = true;

//				NOTIFYING THE SERVER
				logger.info("Notifying");
				if (this.notifyUrl != null && !this.notifyUrl.isEmpty()) {
					try {
						String body = String.format("{\"result\": \"%s\"}", result);
						HttpClientUtil.sendPost(this.notifyUrl, body);
					} catch (Exception e) {
						logger.error("Failed to notify server: {}", e.getMessage());
					}
				}

//				SHOWING A DIALOG
				JOptionPane.showMessageDialog(
					null,
					"Flow evaluation result indicates potential threat: " + result,
					"Threat Detected",
					JOptionPane.WARNING_MESSAGE
				);
				this.stopped = false;
			} else {
				logger.info("Flow evaluation result is benign");
			}
		} else {
			logger.warn("Received empty flow evaluation result");
		}
	}
}
