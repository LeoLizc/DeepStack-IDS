package cic.cs.unb.ca.flow.ui;

import cic.cs.unb.ca.Sys;
import cic.cs.unb.ca.flow.FlowMgr;
import cic.cs.unb.ca.guava.Event.FlowVisualEvent;
import cic.cs.unb.ca.guava.GuavaMgr;
import cic.cs.unb.ca.jnetpcap.BasicFlow;
import cic.cs.unb.ca.jnetpcap.FlowFeature;
import cic.cs.unb.ca.jnetpcap.PcapIfWrapper;
import cic.cs.unb.ca.jnetpcap.worker.LoadPcapInterfaceWorker;
import cic.cs.unb.ca.jnetpcap.worker.TrafficFlowWorker;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jnetpcap.PcapIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cic.cs.unb.ca.jnetpcap.worker.InsertCsvRow;
import swing.common.JTable2CSVWorker;
import swing.common.TextFileFilter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FlowMonitorPane extends JPanel {
    protected static final Logger logger = LoggerFactory.getLogger(FlowMonitorPane.class);

    private JTable flowTable;
    private DefaultTableModel defaultTableModel;
    private JList<PcapIfWrapper> list;
    private DefaultListModel<PcapIfWrapper> listModel;
    private JLabel lblStatus;
    private JLabel lblFlowCnt;
    private int lblCount;
    private boolean showingPane;

    private JTextField urlInputField;

    private TrafficFlowWorker mWorker;

    private JButton btnLoad;
    private JToggleButton btnStart;
    private JToggleButton btnStop;
    private ButtonGroup btnGroup;

    private JButton btnSave = new JButton();
    private File lastSave;
    private JButton btnGraph = new JButton();
    private JFileChooser fileChooser;

    private ExecutorService csvWriterThread;

    public FlowMonitorPane() {
        init();

        setLayout(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        add(initCenterPane(), BorderLayout.CENTER);
    }

    private void init() {
        csvWriterThread = Executors.newSingleThreadExecutor();

        showingPane = false;
    }

    public void destory() {
        csvWriterThread.shutdown();
    }

    private JPanel initCenterPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout(0, 0));
        pane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, initFlowPane(), initNWifsPane());
        splitPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        splitPane.setOneTouchExpandable(true);
        splitPane.setResizeWeight(.5);

        pane.add(splitPane, BorderLayout.CENTER);
        return pane;
    }

    private JPanel initFlowPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout(0, 5));
        pane.setBorder(BorderFactory.createLineBorder(new Color(0x555555)));

        // pane.add(initTableBtnPane(), BorderLayout.NORTH);
        pane.add(initTablePane(), BorderLayout.CENTER);
        pane.add(initStatusPane(), BorderLayout.SOUTH);

        return pane;
    }

    private JPanel initTablePane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout(0, 0));
        pane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create URL input panel with vertical centering
        JPanel urlPanel = new JPanel();
        urlPanel.setLayout(new BoxLayout(urlPanel, BoxLayout.Y_AXIS));

        // Center the panel vertically in the container
        JPanel centeringPanel = new JPanel(new GridBagLayout());

        JLabel urlLabel = new JLabel("Notification Route:");
        urlLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        urlInputField = new JTextField();
        urlInputField.setMaximumSize(new Dimension(400, 25));
        urlInputField.setPreferredSize(new Dimension(400, 25));
        urlInputField.setAlignmentX(Component.CENTER_ALIGNMENT);

        urlPanel.add(Box.createVerticalGlue());
        urlPanel.add(urlLabel);
        urlPanel.add(Box.createVerticalStrut(10));
        urlPanel.add(urlInputField);
        urlPanel.add(Box.createVerticalGlue());

        centeringPanel.add(urlPanel);
        pane.add(centeringPanel, BorderLayout.CENTER);

        return pane;
    }

    private JPanel initTableBtnPane() {
        JPanel btnPane = new JPanel();
        btnPane.setLayout(new BoxLayout(btnPane, BoxLayout.X_AXIS));
        btnSave = new JButton("Save as");
        btnGraph = new JButton("Graphs");
        btnSave.setFocusable(false);
        btnSave.setEnabled(false);
        btnGraph.setFocusable(false);
        btnGraph.setEnabled(false);

        fileChooser = new JFileChooser(new File(FlowMgr.getInstance().getmDataPath()));
        TextFileFilter csvChooserFilter = new TextFileFilter("csv file (*.csv)", new String[] { "csv" });
        fileChooser.setFileFilter(csvChooserFilter);

        btnSave.addActionListener(actionEvent -> {
            // CSV saving is now handled automatically in insertFlow method
            // Show current save location instead
            String path = FlowMgr.getInstance().getSavePath();
            String filename = LocalDate.now().toString() + FlowMgr.FLOW_SUFFIX;
            File currentSaveFile = new File(path, filename);

            if (currentSaveFile.exists()) {
                String msg = "Current flows are being saved to:" + Sys.LINE_SEP + currentSaveFile.getAbsolutePath();
                UIManager.put("OptionPane.minimumSize", new Dimension(0, 0));
                JOptionPane.showMessageDialog(this.getParent(), msg);
                lastSave = currentSaveFile;
                btnGraph.setEnabled(true);
            } else {
                JOptionPane.showMessageDialog(this.getParent(), "No flows have been captured yet.");
            }
        });

        btnGraph.addActionListener(
                actionEvent -> GuavaMgr.getInstance().getEventBus().post(new FlowVisualEvent(lastSave)));

        btnPane.add(Box.createHorizontalGlue());
        btnPane.add(btnSave);
        btnPane.add(Box.createHorizontalGlue());
        btnPane.add(btnGraph);
        btnPane.add(Box.createHorizontalGlue());

        btnPane.setBorder(BorderFactory.createRaisedSoftBevelBorder());

        return btnPane;
    }

    private JPanel initStatusPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.X_AXIS));
        lblStatus = new JLabel("Get ready");
        lblStatus.setForeground(SystemColor.desktop);
        lblFlowCnt = new JLabel("0");
        lblCount = 0;

        pane.add(Box.createHorizontalStrut(5));
        pane.add(lblStatus);
        pane.add(Box.createHorizontalGlue());
        pane.add(lblFlowCnt);
        pane.add(Box.createHorizontalStrut(5));

        return pane;
    }

    private JPanel initNWifsPane() {
        JPanel pane = new JPanel(new BorderLayout(0, 0));
        pane.setBorder(BorderFactory.createLineBorder(new Color(0x555555)));
        pane.add(initNWifsButtonPane(), BorderLayout.WEST);
        pane.add(initNWifsListPane(), BorderLayout.CENTER);

        return pane;
    }

    private JPanel initNWifsButtonPane() {
        JPanel pane = new JPanel();
        pane.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));

        Dimension d = new Dimension(80, 48);

        btnLoad = new JButton("Load");
        btnLoad.setMinimumSize(d);
        btnLoad.setMaximumSize(d);
        btnLoad.addActionListener(actionEvent -> loadPcapIfs());

        btnStart = new JToggleButton("Start");
        btnStart.setMinimumSize(d);
        btnStart.setMaximumSize(d);
        btnStart.setEnabled(false);
        btnStart.addActionListener(actionEvent -> startTrafficFlow());

        btnStop = new JToggleButton("Stop");
        btnStop.setMinimumSize(d);
        btnStop.setMaximumSize(d);
        btnStop.setEnabled(false);
        btnStop.addActionListener(actionEvent -> stopTrafficFlow());

        btnGroup = new ButtonGroup();
        btnGroup.add(btnStart);
        btnGroup.add(btnStop);

        pane.add(Box.createVerticalGlue());
        pane.add(btnLoad);
        pane.add(Box.createVerticalGlue());
        pane.add(btnStart);
        pane.add(Box.createVerticalGlue());
        pane.add(btnStop);
        pane.add(Box.createVerticalGlue());

        return pane;
    }

    private JPanel initNWifsListPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout(0, 0));
        pane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        listModel = new DefaultListModel<>();
        listModel.addElement(new PcapIfWrapper("Click Load button to load network interfaces"));
        list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        pane.add(scrollPane, BorderLayout.CENTER);
        return pane;
    }

    private void loadPcapIfs() {
        LoadPcapInterfaceWorker task = new LoadPcapInterfaceWorker();
        task.addPropertyChangeListener(event -> {
            if ("state".equals(event.getPropertyName())) {
                LoadPcapInterfaceWorker task1 = (LoadPcapInterfaceWorker) event.getSource();
                switch (task1.getState()) {
                    case STARTED:
                        break;
                    case DONE:
                        try {
                            java.util.List<PcapIf> ifs = task1.get();
                            List<PcapIfWrapper> pcapiflist = PcapIfWrapper.fromPcapIf(ifs);

                            listModel.removeAllElements();
                            for (PcapIfWrapper pcapif : pcapiflist) {
                                listModel.addElement(pcapif);
                            }
                            btnStart.setEnabled(true);
                            btnGroup.clearSelection();

                            lblStatus.setText("pick one network interface to listening");
                            lblStatus.validate();

                        } catch (InterruptedException | ExecutionException e) {
                            logger.debug(e.getMessage());
                        }
                        break;
                }
            }
        });
        task.execute();
    }

    private void startTrafficFlow() {

        String ifName = list.getSelectedValue().name();

        if (mWorker != null && !mWorker.isCancelled()) {
            return;
        }

        mWorker = new TrafficFlowWorker(ifName, getNotificationUrl());
        mWorker.addPropertyChangeListener(event -> {
            TrafficFlowWorker task = (TrafficFlowWorker) event.getSource();
            if ("progress".equals(event.getPropertyName())) {
                lblStatus.setText((String) event.getNewValue());
                lblStatus.validate();
            } else if (TrafficFlowWorker.PROPERTY_FLOW.equalsIgnoreCase(event.getPropertyName())) {
                insertFlow((BasicFlow) event.getNewValue());
            } else if(TrafficFlowWorker.PROPERTY_EVALUATION.equalsIgnoreCase(event.getPropertyName()) && !showingPane) {
                String result = (String) event.getNewValue();
                //				SHOWING A DIALOG
                showingPane = true;
                JOptionPane.showMessageDialog(
                        null,
                        "Flow evaluation result indicates potential threat: " + result,
                        "Threat Detected",
                        JOptionPane.WARNING_MESSAGE
                );
                showingPane = false;
            } else if ("state".equals(event.getPropertyName())) {
                switch (task.getState()) {
                    case STARTED:
                        break;
                    case DONE:
                        try {
                            lblStatus.setText(task.get());
                            lblStatus.validate();
                        } catch (CancellationException e) {

                            lblStatus.setText("stop listening");
                            lblStatus.setForeground(SystemColor.GRAY);
                            lblStatus.validate();
                            logger.info("Pcap stop listening");

                        } catch (InterruptedException | ExecutionException e) {
                            logger.debug(e.getMessage());
                        }
                        break;
                }
            }
        });
        mWorker.execute();
        lblStatus.setForeground(SystemColor.desktop);
        btnLoad.setEnabled(false);
        btnStop.setEnabled(true);
        urlInputField.setEnabled(false);
    }

    private void stopTrafficFlow() {

        if (mWorker != null) {
            mWorker.cancel(true);
        }

        // FlowMgr.getInstance().stopFetchFlow();

        btnLoad.setEnabled(true);
        urlInputField.setEnabled(true);

        String path = FlowMgr.getInstance().getAutoSaveFile();
        logger.info("path:{}", path);

        if (new File(path).exists()) {
            String msg = "The flow has been saved to :" + Sys.LINE_SEP +
                    path;

            UIManager.put("OptionPane.minimumSize", new Dimension(0, 0));
            JOptionPane.showMessageDialog(this.getParent(), msg);
        }
    }

    private void insertFlow(BasicFlow flow) {
        List<String> flowStringList = new ArrayList<>();
        String flowDump = flow.dumpFlowBasedFeaturesEx();
        flowStringList.add(flowDump);

        // write flows to csv file
        String header = FlowFeature.getHeader();
        String path = FlowMgr.getInstance().getSavePath();
        String filename = LocalDate.now().toString() + FlowMgr.FLOW_SUFFIX;
        csvWriterThread.execute(new InsertCsvRow(header, flowStringList, path, filename));

        // update flow counter
        if (lblCount <= 999) {
            lblCount++;
            lblFlowCnt.setText(String.valueOf(lblCount));
        } else {
            lblFlowCnt.setText("999+");
        }
        btnSave.setEnabled(true);
    }

    public String getNotificationUrl() {
        return urlInputField.getText();
    }
}
