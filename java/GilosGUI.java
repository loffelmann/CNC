
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import javax.swing.event.*;
import java.io.*;

import filesources.*;
import machines.*;

public class GilosGUI extends Frame
                      implements WindowListener,
                                 ActionListener,
                                 ChangeListener,
                                 DocumentListener {

	// gui texts - čeština
	public static final String MSG_TITLE = "CNC ovladač";
	public static final String MSG_BUTTON_FIND = "najít stroj";
	public static final String MSG_BUTTON_DISCONNECT = "odpojit";
	public static final String MSG_BUTTON_TEST = "zablikat ledkou";
	public static final String MSG_BUTTON_TESTLIM = "test limit";
	public static final String MSG_BUTTON_LOADFILE = "<html>načíst<br/>soubor</html>";
	public static final String MSG_BUTTON_DISCARDFILE = "<html>zahodit<br/>soubor</html>";
	public static final String MSG_Z2D_OFFSET = "odsazení Z";
	public static final String MSG_ZOOM = "zoom";
	public static final String MSG_ZOOMUNIT = "×";
	public static final String MSG_NOTCONNECTED = "nepřipojeno";
	public static final String MSG_SEARCHING = "hledám...";
	public static final String MSG_RUNNING = "%s běží...";
	public static final String MSG_GOINGTOZERO = "do nuly...";
	public static final String MSG_TESTINGLIMITS = "test lim";
	public static final String MSG_XNAME = "X";
	public static final String MSG_YNAME = "Y";
	public static final String MSG_ZNAME = "Z";
	public static final String MSG_UNAME = "U";
	public static final String MSG_VNAME = "V";
	public static final String MSG_WNAME = "W";
	public static final String MSG_AXISFROM = "od";
	public static final String MSG_AXISTO = "do";
	public static final String MSG_UNITLABEL1 = "mm";
	public static final String MSG_UNITLABEL2 = "°   ";
	public static final String MSG_RECALCLABEL1 = "kr/mm";
	public static final String MSG_RECALCLABEL2 = "kr/°   ";
	public static final String MSG_SETAXES = "nastavit osy";
	public static final String MSG_AXISINVERTED = "obráceně";
	public static final String MSG_AXISZEROUP = "nula nahoře";
	public static final String MSG_BACKLASH = " vůle";
	public static final String MSG_BUTTON_START = "start";
	public static final String MSG_BUTTON_STOP = "stop";
	public static final String MSG_BUTTON_ZERO = "zajet do nuly";
	public static final String MSG_BUTTON_RESET = "reset";
	public static final String MSG_BUTTON_BACKSPACE = "backspace";
	public static final String MSG_BUTTON_CLEAR = "smazat";
	public static final String MSG_SCALELABEL = "mm/pix";
	public static final String MSG_SPEEDLABEL = "kr/s";
	public static final String MSG_SELECTFILE = "vyber soubor";
	public static final String MSG_TAB_AXES = "osy";
	public static final String MSG_TAB_TABLET = "tablet";
	public static final String MSG_TAB_CANVAS = "klikadlo";
	public static final String MSG_TAB_FILE = "soubor";
	public static final String MSG_TAB_MANUAL = "posun";
	public static final String MSG_MANUAL_ABSOLUTE = "absolutně";
	public static final String MSG_MANUAL_RELATIVE = "relativně";
	public static final String MSG_BUTTON_MANUALRUN = "jeď";
	public static final String MSG_UNKNOWNPOSITION = "?";
	public static final String MSG_KEYBOARINPUT = "klávesnice";
	public static final String MSG_MANUAL_LIMITS = "hlídat limity";
	public static final String MSG_NOLIMITS = "(nehlídá limity)";
	public static final String MSG_SPINDLESPEED = "kRPM";
	public static final String MSG_BUTTON_SPINDLESPEED = "nastavit";
	public static final String MSG_BUTTON_UP = "nahoru";
	public static final String MSG_BUTTON_DOWN = "dolů";
	public static final String MSG_BUTTON_OPENCHUCK = "otevřít kleštinu";
	public static final String MSG_BUTTON_CLOSECHUCK = "zavřít kleštinu";

	// status bar messages - čeština
	public static final String MSG_FILE_BADEXT = "Neznámý typ souboru: %s";
	public static final String MSG_FILE_EXTMISMATCH = "Přípona %s nesedí na osy %s";
	public static final String MSG_FILE_CANTOPEN = "Problém při otvírání souboru";
	public static final String MSG_FILE_CANTREAD = "Problém při čtení souboru";
	public static final String MSG_FILE_LOADED = "Načten soubor s %d body";
	public static final String MSG_ERROR_BIGBACKLASH = "Moc velká vůle (max 32000 kroků)";
	public static final String MSG_ERROR_UNKNOWN = "Něco je špatně";
	public static final String MSG_INPUTMODE_FILE = "Cesta z %s";
	public static final String MSG_INPUTMODE_CANVAS = "Cesta z klikadla";
	public static final String MSG_INPUTMODE_DONE = "Cesta ukončena";
	public static final String MSG_INPUTMODE_MANUAL = "Manuál";
	public static final String MSG_KILLED = "Konec...";

	// commands

	public static final String COMMAND_LOADFILE = "load file";

	public static final String MODE_LIMIT = "limit";
	public static final String MODE_ZERO = "zero";
	public static final String MODE_RUN = "run";


	private MachineConfig machineConf;
	private Machine machine;

	// Components changing label or other state
	private Button connectButton;
	private Label portLabel;
	private Button startButton;
	private JButton loadFileButton;
	private JButton chuckButton1, chuckButton2;
	private Label statusBar;
	private JLabel positionX, positionY, positionZ, positionU, positionV, positionW;
	private JLabel xRecalcLabel, yRecalcLabel, zRecalcLabel;
	private PreviewCanvas previewCanvas;

	// Components holding configuration
	private Checkbox xAxisExists, yAxisExists, zAxisExists, uAxisExists, vAxisExists, wAxisExists;
	private JTextField xAxisRecalc, yAxisRecalc, zAxisRecalc, uAxisRecalc, vAxisRecalc, wAxisRecalc;
	private Checkbox xAxisInverted, yAxisInverted, zAxisInverted, uAxisInverted, vAxisInverted, wAxisInverted;
	private Checkbox xAxisZeroUp, yAxisZeroUp, zAxisZeroUp, uAxisZeroUp, vAxisZeroUp, wAxisZeroUp;
	private JTextField xAxisFrom, yAxisFrom, zAxisFrom, uAxisFrom, vAxisFrom, wAxisFrom;
	private JTextField xAxisTo, yAxisTo, zAxisTo, uAxisTo, vAxisTo, wAxisTo;
	private JTextField xAxisBacklash, yAxisBacklash, zAxisBacklash, uAxisBacklash, vAxisBacklash, wAxisBacklash;
	private JTextField manualX, manualY, manualZ, manualU, manualV, manualW;
	private JTextField canvasScale;
	private JTextField z2DOffset, zoom;
	private TextField defaultSpeed;
	private JComboBox<String> manualMode;
	private JCheckBox manualStopAtLimits;
	private JButton xRunButton, yRunButton, zRunButton, uRunButton, vRunButton, wRunButton;
	private JTextField spindleInput;

	// Components enabled/disabled by state
	private boolean connected = false;
	private boolean running = false;
	private List<Component> connectedOnly;
	private List<Component> stoppedOnly;

	// Data sources
	private TestDrawCanvas canvas;
	private FileSource fileSource;

	private double[] fileStart = new double[3];
	private double[] firstFilePoint;

	private boolean waitForFileToolPathEnd = false;

	private boolean touchButtonActive = false;

	private GilosController controller;


	public GilosGUI(GilosController controller){
		this.controller = controller;

		this.machine = new XYZMachine(); // ?? allow different machine types

		connectedOnly = new ArrayList<Component>();
		stoppedOnly = new ArrayList<Component>();

		setLayout(new GridBagLayout());
		var topConstraints = new GridBagConstraints();
		topConstraints.gridx = 0;
		topConstraints.gridy = 0;
		topConstraints.weightx = 1;
		topConstraints.weighty = 1;
		topConstraints.fill = GridBagConstraints.BOTH;

		JPanel separator;

		// connect buttons /////////////////////////////////////////////////////

		var buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		connectButton = new Button(MSG_BUTTON_FIND);
		connectButton.addActionListener(controller);
		connectButton.setActionCommand(controller.COMMAND_CONNECT);
		stoppedOnly.add(connectButton);
		buttons.add(connectButton);

		portLabel = new Label(MSG_NOTCONNECTED);
		buttons.add(portLabel);

		add(buttons, topConstraints);
		topConstraints.gridy++;

		// test buttons ////////////////////////////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		var blinkButton = new Button(MSG_BUTTON_TEST);
		blinkButton.addActionListener(controller);
		blinkButton.setActionCommand(controller.COMMAND_BLINK);
		connectedOnly.add(blinkButton);
		stoppedOnly.add(blinkButton);
		buttons.add(blinkButton);

		var testLimButton = new Button(MSG_BUTTON_TESTLIM);
		testLimButton.addActionListener(controller);
		testLimButton.setActionCommand(controller.COMMAND_TESTLIM);
		connectedOnly.add(testLimButton);
		stoppedOnly.add(testLimButton);
		buttons.add(testLimButton);

		var resetButton = new Button(MSG_BUTTON_RESET);
		resetButton.addActionListener(controller);
		resetButton.setActionCommand(controller.COMMAND_RESET);
		buttons.add(resetButton);

		add(buttons, topConstraints);
		topConstraints.gridy++;

		// tab pane ////////////////////////////////////////////////////////////

		topConstraints.weighty = 0;
		add(new JLabel(" "), topConstraints); // separator
		topConstraints.weighty = 1;
		topConstraints.gridy++;

		var tabs = new JTabbedPane();
		tabs.addChangeListener(this);
		topConstraints.weighty = 10;
		add(tabs, topConstraints);
		topConstraints.weighty = 1;
		topConstraints.gridy++;

		// axis settings ///////////////////////////////////////////////////////

		var axesTab = new Panel();
		axesTab.setLayout(new GridBagLayout());
		var cons = new GridBagConstraints();
		cons.fill = GridBagConstraints.HORIZONTAL;

		// X axis ////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		xAxisExists = new Checkbox(MSG_XNAME, true);
		connectedOnly.add(xAxisExists);
		stoppedOnly.add(xAxisExists);
		buttons.add(xAxisExists);

		xAxisInverted = new Checkbox(MSG_AXISINVERTED, false);
		connectedOnly.add(xAxisInverted);
		stoppedOnly.add(xAxisInverted);
		buttons.add(xAxisInverted);

		xAxisZeroUp = new Checkbox(MSG_AXISZEROUP, false);
		connectedOnly.add(xAxisZeroUp);
		stoppedOnly.add(xAxisZeroUp);
		buttons.add(xAxisZeroUp);

		xAxisBacklash = new JTextField("0", 4);
		connectedOnly.add(xAxisBacklash);
		stoppedOnly.add(xAxisBacklash);
		buttons.add(new JLabel(MSG_BACKLASH));
		buttons.add(xAxisBacklash);
		buttons.add(new JLabel(MSG_UNITLABEL1));

		cons.gridy = 0;
		axesTab.add(buttons, cons);

		////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		xAxisRecalc = new JTextField("200", 3);
		connectedOnly.add(xAxisRecalc);
		stoppedOnly.add(xAxisRecalc);
		buttons.add(xAxisRecalc);
		buttons.add(new JLabel(MSG_RECALCLABEL1+" "));

		xAxisFrom = new JTextField("0", 6);
		connectedOnly.add(xAxisFrom);
		stoppedOnly.add(xAxisFrom);
		buttons.add(new JLabel(MSG_AXISFROM+" "));
		buttons.add(xAxisFrom);

		xAxisTo = new JTextField("1000", 6);
		connectedOnly.add(xAxisTo);
		stoppedOnly.add(xAxisTo);
		buttons.add(new JLabel(MSG_AXISTO+" "));
		buttons.add(xAxisTo);
		buttons.add(new JLabel(MSG_UNITLABEL1));

		cons.gridy++;
		axesTab.add(buttons, cons);

		// separator /////////////////////////////

		cons.gridy++;
		axesTab.add(new JSeparator(), cons);

		// Y axis ////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		yAxisExists = new Checkbox(MSG_YNAME, true);
		connectedOnly.add(yAxisExists);
		stoppedOnly.add(yAxisExists);
		buttons.add(yAxisExists);

		yAxisInverted = new Checkbox(MSG_AXISINVERTED, false);
		connectedOnly.add(yAxisInverted);
		stoppedOnly.add(yAxisInverted);
		buttons.add(yAxisInverted);

		yAxisZeroUp = new Checkbox(MSG_AXISZEROUP, false);
		connectedOnly.add(yAxisZeroUp);
		stoppedOnly.add(yAxisZeroUp);
		buttons.add(yAxisZeroUp);

		yAxisBacklash = new JTextField("0", 4);
		connectedOnly.add(yAxisBacklash);
		stoppedOnly.add(yAxisBacklash);
		buttons.add(new JLabel(MSG_BACKLASH));
		buttons.add(yAxisBacklash);
		buttons.add(new JLabel(MSG_UNITLABEL1));

		cons.gridy++;
		axesTab.add(buttons, cons);

		////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		yAxisRecalc = new JTextField("200", 3);
		connectedOnly.add(yAxisRecalc);
		stoppedOnly.add(yAxisRecalc);
		buttons.add(yAxisRecalc);
		buttons.add(new JLabel(MSG_RECALCLABEL1+" "));

		yAxisFrom = new JTextField("0", 6);
		connectedOnly.add(yAxisFrom);
		stoppedOnly.add(yAxisFrom);
		buttons.add(new JLabel(MSG_AXISFROM+" "));
		buttons.add(yAxisFrom);

		yAxisTo = new JTextField("1000", 6);
		connectedOnly.add(yAxisTo);
		stoppedOnly.add(yAxisTo);
		buttons.add(new JLabel(MSG_AXISTO+" "));
		buttons.add(yAxisTo);
		buttons.add(new JLabel(MSG_UNITLABEL1));

		cons.gridy++;
		axesTab.add(buttons, cons);

		// separator /////////////////////////////

		cons.gridy++;
		axesTab.add(new JSeparator(), cons);

		// Z axis ////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		zAxisExists = new Checkbox(MSG_ZNAME, false);
		connectedOnly.add(zAxisExists);
		stoppedOnly.add(zAxisExists);
		buttons.add(zAxisExists);

		zAxisInverted = new Checkbox(MSG_AXISINVERTED, false);
		connectedOnly.add(zAxisInverted);
		stoppedOnly.add(zAxisInverted);
		buttons.add(zAxisInverted);

		zAxisZeroUp = new Checkbox(MSG_AXISZEROUP, false);
		connectedOnly.add(zAxisZeroUp);
		stoppedOnly.add(zAxisZeroUp);
		buttons.add(zAxisZeroUp);

		zAxisBacklash = new JTextField("0", 4);
		connectedOnly.add(zAxisBacklash);
		stoppedOnly.add(zAxisBacklash);
		buttons.add(new JLabel(MSG_BACKLASH));
		buttons.add(zAxisBacklash);
		buttons.add(new JLabel(MSG_UNITLABEL1));

		cons.gridy++;
		axesTab.add(buttons, cons);

		////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		zAxisRecalc = new JTextField("200", 3);
		connectedOnly.add(zAxisRecalc);
		stoppedOnly.add(zAxisRecalc);
		buttons.add(zAxisRecalc);
		buttons.add(new JLabel(MSG_RECALCLABEL1+" "));

		zAxisFrom = new JTextField("0", 6);
		connectedOnly.add(zAxisFrom);
		stoppedOnly.add(zAxisFrom);
		buttons.add(new JLabel(MSG_AXISFROM+" "));
		buttons.add(zAxisFrom);

		zAxisTo = new JTextField("1000", 6);
		connectedOnly.add(zAxisTo);
		stoppedOnly.add(zAxisTo);
		buttons.add(new JLabel(MSG_AXISTO+" "));
		buttons.add(zAxisTo);
		buttons.add(new JLabel(MSG_UNITLABEL1));

		cons.gridy++;
		axesTab.add(buttons, cons);

		// separator /////////////////////////////

		cons.gridy++;
		axesTab.add(new JSeparator(), cons);

		// U axis ////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		uAxisExists = new Checkbox(MSG_UNAME, false);
		connectedOnly.add(uAxisExists);
		stoppedOnly.add(uAxisExists);
		buttons.add(uAxisExists);

		uAxisInverted = new Checkbox(MSG_AXISINVERTED, false);
		connectedOnly.add(uAxisInverted);
		stoppedOnly.add(uAxisInverted);
		buttons.add(uAxisInverted);

		uAxisZeroUp = new Checkbox(MSG_AXISZEROUP, false);
		connectedOnly.add(uAxisZeroUp);
		stoppedOnly.add(uAxisZeroUp);
		buttons.add(uAxisZeroUp);

		uAxisBacklash = new JTextField("0", 4);
		connectedOnly.add(uAxisBacklash);
		stoppedOnly.add(uAxisBacklash);
		buttons.add(new JLabel(MSG_BACKLASH));
		buttons.add(uAxisBacklash);
		buttons.add(new JLabel(MSG_UNITLABEL2));

		cons.gridy++;
		axesTab.add(buttons, cons);

		////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		uAxisRecalc = new JTextField("200", 3);
		connectedOnly.add(uAxisRecalc);
		stoppedOnly.add(uAxisRecalc);
		buttons.add(uAxisRecalc);
		buttons.add(new JLabel(MSG_RECALCLABEL2+" "));

		uAxisFrom = new JTextField("", 6);
		connectedOnly.add(uAxisFrom);
		stoppedOnly.add(uAxisFrom);
		buttons.add(new JLabel(MSG_AXISFROM+" "));
		buttons.add(uAxisFrom);

		uAxisTo = new JTextField("", 6);
		connectedOnly.add(uAxisTo);
		stoppedOnly.add(uAxisTo);
		buttons.add(new JLabel(MSG_AXISTO+" "));
		buttons.add(uAxisTo);
		buttons.add(new JLabel(MSG_UNITLABEL2));

		cons.gridy++;
		axesTab.add(buttons, cons);

		// separator /////////////////////////////

		cons.gridy++;
		axesTab.add(new JSeparator(), cons);

		// V axis ////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		vAxisExists = new Checkbox(MSG_VNAME, false);
		connectedOnly.add(vAxisExists);
		stoppedOnly.add(vAxisExists);
		buttons.add(vAxisExists);

		vAxisInverted = new Checkbox(MSG_AXISINVERTED, false);
		connectedOnly.add(vAxisInverted);
		stoppedOnly.add(vAxisInverted);
		buttons.add(vAxisInverted);

		vAxisZeroUp = new Checkbox(MSG_AXISZEROUP, false);
		connectedOnly.add(vAxisZeroUp);
		stoppedOnly.add(vAxisZeroUp);
		buttons.add(vAxisZeroUp);

		vAxisBacklash = new JTextField("0", 4);
		connectedOnly.add(vAxisBacklash);
		stoppedOnly.add(vAxisBacklash);
		buttons.add(new JLabel(MSG_BACKLASH));
		buttons.add(vAxisBacklash);
		buttons.add(new JLabel(MSG_UNITLABEL2));

		cons.gridy++;
		axesTab.add(buttons, cons);

		////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		vAxisRecalc = new JTextField("200", 3);
		connectedOnly.add(vAxisRecalc);
		stoppedOnly.add(vAxisRecalc);
		buttons.add(vAxisRecalc);
		buttons.add(new JLabel(MSG_RECALCLABEL2+" "));

		vAxisFrom = new JTextField("", 6);
		connectedOnly.add(vAxisFrom);
		stoppedOnly.add(vAxisFrom);
		buttons.add(new JLabel(MSG_AXISFROM+" "));
		buttons.add(vAxisFrom);

		vAxisTo = new JTextField("", 6);
		connectedOnly.add(vAxisTo);
		stoppedOnly.add(vAxisTo);
		buttons.add(new JLabel(MSG_AXISTO+" "));
		buttons.add(vAxisTo);
		buttons.add(new JLabel(MSG_UNITLABEL2));

		cons.gridy++;
		axesTab.add(buttons, cons);

		// separator /////////////////////////////

		cons.gridy++;
		axesTab.add(new JSeparator(), cons);

		// W axis ////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		wAxisExists = new Checkbox(MSG_WNAME, false);
		connectedOnly.add(wAxisExists);
		stoppedOnly.add(wAxisExists);
		buttons.add(wAxisExists);

		wAxisInverted = new Checkbox(MSG_AXISINVERTED, false);
		connectedOnly.add(wAxisInverted);
		stoppedOnly.add(wAxisInverted);
		buttons.add(wAxisInverted);

		wAxisZeroUp = new Checkbox(MSG_AXISZEROUP, false);
		connectedOnly.add(wAxisZeroUp);
		stoppedOnly.add(wAxisZeroUp);
		buttons.add(wAxisZeroUp);

		wAxisBacklash = new JTextField("0", 4);
		connectedOnly.add(wAxisBacklash);
		stoppedOnly.add(wAxisBacklash);
		buttons.add(new JLabel(MSG_BACKLASH));
		buttons.add(wAxisBacklash);
		buttons.add(new JLabel(MSG_UNITLABEL2));

		cons.gridy++;
		axesTab.add(buttons, cons);

		////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		vAxisRecalc = new JTextField("200", 3);
		connectedOnly.add(vAxisRecalc);
		stoppedOnly.add(vAxisRecalc);
		buttons.add(vAxisRecalc);
		buttons.add(new JLabel(MSG_RECALCLABEL2+" "));

		vAxisFrom = new JTextField("", 6);
		connectedOnly.add(vAxisFrom);
		stoppedOnly.add(vAxisFrom);
		buttons.add(new JLabel(MSG_AXISFROM+" "));
		buttons.add(vAxisFrom);

		vAxisTo = new JTextField("", 6);
		connectedOnly.add(vAxisTo);
		stoppedOnly.add(vAxisTo);
		buttons.add(new JLabel(MSG_AXISTO+" "));
		buttons.add(vAxisTo);
		buttons.add(new JLabel(MSG_UNITLABEL2));

		cons.gridy++;
		axesTab.add(buttons, cons);


		//////////////////////////////////////////

		tabs.add(MSG_TAB_AXES, axesTab);

		// file input //////////////////////////////////////////////////////////

		var fileTab = new Panel();
		fileTab.setLayout(new GridBagLayout());
		cons = new GridBagConstraints();

		// preview ///////////////////////////////

		previewCanvas = new PreviewCanvas();
		previewCanvas.setSize(340, 260);
		cons.gridx = 0;
		cons.gridy = 0;
		cons.gridwidth = 7;
		fileTab.add(previewCanvas, cons);
		cons.gridwidth = 1;

		// load button ///////////////////////////

		loadFileButton = new JButton(MSG_BUTTON_LOADFILE);
		loadFileButton.addActionListener(this);
		loadFileButton.setActionCommand(COMMAND_LOADFILE);
//		connectedOnly.add(loadFileButton);
//		stoppedOnly.add(loadFileButton);

		cons.gridy++;
		cons.gridheight = 3;
		cons.fill = GridBagConstraints.VERTICAL;
		fileTab.add(loadFileButton, cons);
		cons.gridheight = 1;
		cons.fill = GridBagConstraints.HORIZONTAL;

		// settings //////////////////////////////

		// X recalc

		cons.gridx++;
		cons.gridwidth = 6;

		xRecalcLabel = new JLabel(
			"   "
			+ MSG_XNAME + " "
			+ MSG_AXISFROM + " 000.00 "
			+ MSG_AXISTO + " 000.00 "
			+ MSG_UNITLABEL1
		, SwingConstants.LEFT);
		fileTab.add(xRecalcLabel, cons);

		// Y recalc

		cons.gridy++;

		yRecalcLabel = new JLabel(
			"   "
			+ MSG_YNAME + " "
			+ MSG_AXISFROM + " 000.00 "
			+ MSG_AXISTO + " 000.00 "
			+ MSG_UNITLABEL1
		, SwingConstants.LEFT);
		fileTab.add(yRecalcLabel, cons);

		// Z recalc

		cons.gridy++;

		zRecalcLabel = new JLabel(
			"   "
			+ MSG_ZNAME + " "
			+ MSG_AXISFROM + " 000.00 "
			+ MSG_AXISTO + " 000.00 "
			+ MSG_UNITLABEL1
		, SwingConstants.LEFT);
		fileTab.add(zRecalcLabel, cons);

		cons.gridwidth = 1;

		// Z 2D offset

		cons.gridy++;
		cons.gridx = 0;

		var z2DLabel = new JLabel(MSG_Z2D_OFFSET+" ", SwingConstants.RIGHT);
		fileTab.add(z2DLabel, cons);

		z2DOffset = new JTextField("0", 4);
		cons.gridx++;
		fileTab.add(z2DOffset, cons);

		var z2DUnit = new JLabel(" "+MSG_UNITLABEL1, SwingConstants.LEFT);
		cons.gridx++;
		fileTab.add(z2DUnit, cons);

		// zoom

		var zoomLabel = new JLabel("   "+MSG_ZOOM+" ", SwingConstants.RIGHT);
		cons.gridx++;
		fileTab.add(zoomLabel, cons);

		zoom = new JTextField("1", 4);
		cons.gridx++;
		fileTab.add(zoom, cons);
		zoom.getDocument().addDocumentListener(this);

		var zoomUnit = new JLabel(" "+MSG_ZOOMUNIT, SwingConstants.LEFT);
		cons.gridx++;
		fileTab.add(zoomUnit, cons);

		//////////////////////

		tabs.add(MSG_TAB_FILE, fileTab);

		// manual input ////////////////////////////////////////////////////////

		var manualTab = new Panel();
		manualTab.setLayout(new GridBagLayout());
		cons = new GridBagConstraints();
		cons.fill = GridBagConstraints.HORIZONTAL;

		cons.gridx = 0;
		cons.gridy = 0;
		manualTab.add(new JLabel(" "), cons); // spacer

		// settings //////////////////////////////

		cons.gridy++;

		manualMode = new JComboBox<String>();
		manualMode.addItem(MSG_MANUAL_RELATIVE);
		manualMode.addItem(MSG_MANUAL_ABSOLUTE);
		cons.gridx = 0;
		cons.gridwidth = 4;
		manualTab.add(manualMode, cons);
		cons.gridwidth = 1;

		cons.gridx += 4;
		manualTab.add(new JLabel(" "), cons); // vertical separator

		manualStopAtLimits = new JCheckBox(MSG_MANUAL_LIMITS, true);
		cons.gridx++;
		cons.gridwidth = 3;
		manualTab.add(manualStopAtLimits, cons);
		cons.gridwidth = 1;

		cons.gridy++;
		manualTab.add(new JLabel(" "), cons); // horizontal separator

		// X /////////////////////////////////////

		cons.gridy++;
		cons.gridx = 0;
		manualTab.add(new JLabel(MSG_XNAME+" "), cons);

		positionX = new JLabel("?", SwingConstants.RIGHT);
		cons.gridx++;
		manualTab.add(positionX, cons);

		cons.gridx++;
		manualTab.add(new JLabel(" "+MSG_UNITLABEL1), cons);

		manualX = new JTextField("0", 6);
		cons.gridx++;
		manualTab.add(manualX, cons);

		xRunButton = new JButton(MSG_BUTTON_MANUALRUN);
		xRunButton.addActionListener(controller);
		xRunButton.setActionCommand(controller.COMMAND_NUMRUN_X);
		connectedOnly.add(xRunButton);
		stoppedOnly.add(xRunButton);
		cons.gridx++;
		manualTab.add(new JLabel(" "), cons);
		cons.gridx++;
		manualTab.add(xRunButton, cons);

		// Y /////////////////////////////////////

		cons.gridy++;
		cons.gridx = 0;
		manualTab.add(new JLabel(MSG_YNAME+" "), cons);

		positionY = new JLabel("?", SwingConstants.RIGHT);
		cons.gridx++;
		manualTab.add(positionY, cons);

		cons.gridx++;
		manualTab.add(new JLabel(" "+MSG_UNITLABEL1), cons);

		manualY = new JTextField("0", 6);
		cons.gridx++;
		manualTab.add(manualY, cons);

		yRunButton = new JButton(MSG_BUTTON_MANUALRUN);
		yRunButton.addActionListener(controller);
		yRunButton.setActionCommand(controller.COMMAND_NUMRUN_Y);
		connectedOnly.add(yRunButton);
		stoppedOnly.add(yRunButton);
		cons.gridx++;
		manualTab.add(new JLabel(" "), cons);
		cons.gridx++;
		manualTab.add(yRunButton, cons);

		// Z /////////////////////////////////////

		cons.gridy++;
		cons.gridx = 0;
		manualTab.add(new JLabel(MSG_ZNAME+" "), cons);

		positionZ = new JLabel("?", SwingConstants.RIGHT);
		cons.gridx++;
		manualTab.add(positionZ, cons);

		cons.gridx++;
		manualTab.add(new JLabel(" "+MSG_UNITLABEL1), cons);

		manualZ = new JTextField("0", 6);
		cons.gridx++;
		manualTab.add(manualZ, cons);

		zRunButton = new JButton(MSG_BUTTON_MANUALRUN);
		zRunButton.addActionListener(controller);
		zRunButton.setActionCommand(controller.COMMAND_NUMRUN_Z);
		connectedOnly.add(zRunButton);
		stoppedOnly.add(zRunButton);
		cons.gridx++;
		manualTab.add(new JLabel(" "), cons);
		cons.gridx++;
		manualTab.add(zRunButton, cons);

		// U /////////////////////////////////////

		cons.gridy++;
		cons.gridx = 0;
		manualTab.add(new JLabel(MSG_UNAME+" "), cons);

		positionU = new JLabel("?", SwingConstants.RIGHT);
		cons.gridx++;
		manualTab.add(positionU, cons);

		cons.gridx++;
		manualTab.add(new JLabel(" "+MSG_UNITLABEL2), cons);

		manualU = new JTextField("0", 6);
		cons.gridx++;
		manualTab.add(manualU, cons);

		uRunButton = new JButton(MSG_BUTTON_MANUALRUN);
		uRunButton.addActionListener(controller);
		uRunButton.setActionCommand(controller.COMMAND_NUMRUN_U);
		connectedOnly.add(uRunButton);
		stoppedOnly.add(uRunButton);
		cons.gridx++;
		manualTab.add(new JLabel(" "), cons);
		cons.gridx++;
		manualTab.add(uRunButton, cons);

		// V /////////////////////////////////////

		cons.gridy++;
		cons.gridx = 0;
		manualTab.add(new JLabel(MSG_VNAME+" "), cons);

		positionV = new JLabel("?", SwingConstants.RIGHT);
		positionV.setSize(30, 200);
		cons.gridx++;
		manualTab.add(positionV, cons);

		cons.gridx++;
		manualTab.add(new JLabel(" "+MSG_UNITLABEL2), cons);

		manualV = new JTextField("0", 6);
		cons.gridx++;
		manualTab.add(manualV, cons);

		vRunButton = new JButton(MSG_BUTTON_MANUALRUN);
		vRunButton.addActionListener(controller);
		vRunButton.setActionCommand(controller.COMMAND_NUMRUN_V);
		connectedOnly.add(vRunButton);
		stoppedOnly.add(vRunButton);
		cons.gridx++;
		manualTab.add(new JLabel(" "), cons);
		cons.gridx++;
		manualTab.add(vRunButton, cons);

		// W /////////////////////////////////////

		cons.gridy++;
		cons.gridx = 0;
		manualTab.add(new JLabel(MSG_WNAME+" "), cons);

		positionV = new JLabel("?", SwingConstants.RIGHT);
		positionV.setSize(30, 200);
		cons.gridx++;
		manualTab.add(positionV, cons);

		cons.gridx++;
		manualTab.add(new JLabel(" "+MSG_UNITLABEL2), cons);

		manualW = new JTextField("0", 6);
		cons.gridx++;
		manualTab.add(manualW, cons);

		wRunButton = new JButton(MSG_BUTTON_MANUALRUN);
		wRunButton.addActionListener(controller);
		wRunButton.setActionCommand(controller.COMMAND_NUMRUN_W);
		connectedOnly.add(wRunButton);
		stoppedOnly.add(wRunButton);
		cons.gridx++;
		manualTab.add(new JLabel(" "), cons);
		cons.gridx++;
		manualTab.add(wRunButton, cons);

		// numerical run button //////////////////

		cons.gridy = 3;
		cons.gridx++;
		manualTab.add(new JLabel(" "), cons);

		var manualRunButton = new JButton(MSG_BUTTON_MANUALRUN);
		manualRunButton.addActionListener(controller);
		manualRunButton.setActionCommand(controller.COMMAND_NUMRUN);
		connectedOnly.add(manualRunButton);
		stoppedOnly.add(manualRunButton);
		cons.gridx++;
		cons.gridheight = 6;
		cons.fill = GridBagConstraints.BOTH;
		manualTab.add(manualRunButton, cons);
		cons.gridheight = 1;
		cons.fill = GridBagConstraints.HORIZONTAL;

		// keyboard input ////////////////////////

		var keyboardInput = new JTextField("", 6);
//		keyboardInput.setEditable(false);
		connectedOnly.add(keyboardInput);
//		stoppedOnly.add(keyboardInput);
		keyboardInput.addKeyListener(new KeyAdapter(){
			public synchronized void keyPressed(KeyEvent ev){
				if(!connected || running)return;
				System.out.println("keydown "+keyCodeToDebug(ev.getKeyCode()));
				switch(ev.getKeyCode()){
					case KeyEvent.VK_NUMPAD4:
						controller.interactiveMove(0, -1);
						break;
					case KeyEvent.VK_NUMPAD6:
						controller.interactiveMove(0, +1);
						break;
					case KeyEvent.VK_NUMPAD2:
						controller.interactiveMove(1, -1);
						break;
					case KeyEvent.VK_NUMPAD8:
						controller.interactiveMove(1, +1);
						break;
					case KeyEvent.VK_NUMPAD3:
						controller.interactiveMove(2, -1);
						break;
					case KeyEvent.VK_NUMPAD9:
						controller.interactiveMove(2, +1);
						break;
					case KeyEvent.VK_LEFT:
						controller.interactiveMove(3, -1);
						break;
					case KeyEvent.VK_RIGHT:
						controller.interactiveMove(3, +1);
						break;
					case KeyEvent.VK_DOWN:
						controller.interactiveMove(4, -1);
						break;
					case KeyEvent.VK_UP:
						controller.interactiveMove(4, +1);
						break;
					case KeyEvent.VK_PAGE_DOWN:
						controller.interactiveMove(5, -1);
						break;
					case KeyEvent.VK_PAGE_UP:
						controller.interactiveMove(5, +1);
						break;
				}
			}
			public synchronized void keyTyped(KeyEvent ev){
				keyboardInput.setText("");
			}
			public synchronized void keyReleased(KeyEvent ev){
				System.out.println("keyup "+keyCodeToDebug(ev.getKeyCode()));
				keyboardInput.setText("");
				if(!connected)return;
				switch(ev.getKeyCode()){
					case KeyEvent.VK_NUMPAD4:
					case KeyEvent.VK_NUMPAD6:
					case KeyEvent.VK_NUMPAD2:
					case KeyEvent.VK_NUMPAD8:
					case KeyEvent.VK_NUMPAD9:
					case KeyEvent.VK_NUMPAD3:
					case KeyEvent.VK_LEFT:
					case KeyEvent.VK_RIGHT:
					case KeyEvent.VK_DOWN:
					case KeyEvent.VK_UP:
					case KeyEvent.VK_PAGE_DOWN:
					case KeyEvent.VK_PAGE_UP:
						controller.interactiveStop();
				}
			}
		});

		cons.gridx = 0;
		cons.gridy = 8;
		manualTab.add(new JLabel(" "), cons); // spacer
		cons.gridx = 0;
		cons.gridy++;
		manualTab.add(new JLabel(" "), cons); // spacer

		cons.gridy++;
		cons.gridwidth = 3;
		manualTab.add(new JLabel(MSG_KEYBOARINPUT+" "), cons);

		cons.gridx += cons.gridwidth;
		cons.gridwidth = 1;
		manualTab.add(keyboardInput, cons);

		cons.gridx += 2;
		cons.gridwidth = 3;
		manualTab.add(new JLabel(MSG_NOLIMITS), cons);
		cons.gridwidth = 1;

		// spindle speed control /////////////////

		cons.gridx = 0;
		cons.gridy++;
		manualTab.add(new JLabel(" "), cons); // spacer

		cons.gridy++;
		cons.gridwidth = 3;
		manualTab.add(new JLabel(MSG_SPINDLESPEED+" "), cons);

		spindleInput = new JTextField("", 6);
		connectedOnly.add(spindleInput);
		cons.gridx = 3;
		cons.gridwidth = 1;
		manualTab.add(spindleInput, cons);

		var spindleButton = new JButton(MSG_BUTTON_SPINDLESPEED);
		spindleButton.setActionCommand(controller.COMMAND_SETSPINDLESPEED);
		spindleButton.addActionListener(controller);
		connectedOnly.add(spindleButton);
		cons.gridx = 5;
		cons.gridwidth = 3;
		manualTab.add(spindleButton, cons);
		cons.gridwidth = 1;

		// chuck control /////////////////////////

		cons.gridx = 0;
		cons.gridy++;
		manualTab.add(new JLabel(" "), cons); // spacer

		cons.gridy++;
		cons.gridwidth = 8;
		chuckButton1 = new JButton(MSG_BUTTON_OPENCHUCK);
		chuckButton1.setActionCommand(controller.COMMAND_OPENCHUCK);
		chuckButton1.addActionListener(controller);
		connectedOnly.add(chuckButton1);
		manualTab.add(chuckButton1, cons);
		cons.gridwidth = 1;

		cons.gridx = 0;
		cons.gridy++;
		manualTab.add(new JLabel(" "), cons); // spacer

		tabs.add(MSG_TAB_MANUAL, manualTab);

		// movement buttons for touch screen ///////////////////////////////////

		var tabletTab = new Panel();
		tabletTab.setLayout(new GridBagLayout());
		cons = new GridBagConstraints();
		cons.fill = GridBagConstraints.BOTH;
		cons.weightx = 1;
		cons.weighty = 1;

		var upButton = new JButton(MSG_XNAME+" "+MSG_BUTTON_UP);
		upButton.setActionCommand(controller.COMMAND_INTRUN_X_UP);
		upButton.getModel().addChangeListener(this);
		connectedOnly.add(upButton);
		stoppedOnly.add(upButton);
		cons.gridy = 1;
		cons.gridx = 1;
		tabletTab.add(upButton, cons);

		var downButton = new JButton(MSG_XNAME+" "+MSG_BUTTON_DOWN);
		downButton.setActionCommand(controller.COMMAND_INTRUN_X_DOWN);
		downButton.getModel().addChangeListener(this);
		connectedOnly.add(downButton);
		stoppedOnly.add(downButton);
		cons.gridy = 2;
		cons.gridx = 1;
		tabletTab.add(downButton, cons);

		upButton = new JButton(MSG_YNAME+" "+MSG_BUTTON_UP);
		upButton.setActionCommand(controller.COMMAND_INTRUN_Y_UP);
		upButton.getModel().addChangeListener(this);
		connectedOnly.add(upButton);
		stoppedOnly.add(upButton);
		cons.gridy = 1;
		cons.gridx = 3;
		tabletTab.add(upButton, cons);

		downButton = new JButton(MSG_YNAME+" "+MSG_BUTTON_DOWN);
		downButton.setActionCommand(controller.COMMAND_INTRUN_Y_DOWN);
		downButton.getModel().addChangeListener(this);
		connectedOnly.add(downButton);
		stoppedOnly.add(downButton);
		cons.gridy = 2;
		cons.gridx = 3;
		tabletTab.add(downButton, cons);

		upButton = new JButton(MSG_ZNAME+" "+MSG_BUTTON_UP);
		upButton.setActionCommand(controller.COMMAND_INTRUN_Z_UP);
		upButton.getModel().addChangeListener(this);
		connectedOnly.add(upButton);
		stoppedOnly.add(upButton);
		cons.gridy = 1;
		cons.gridx = 5;
		tabletTab.add(upButton, cons);

		downButton = new JButton(MSG_ZNAME+" "+MSG_BUTTON_DOWN);
		downButton.setActionCommand(controller.COMMAND_INTRUN_Z_DOWN);
		downButton.getModel().addChangeListener(this);
		connectedOnly.add(downButton);
		stoppedOnly.add(downButton);
		cons.gridy = 2;
		cons.gridx = 5;
		tabletTab.add(downButton, cons);

		upButton = new JButton(MSG_UNAME+" "+MSG_BUTTON_UP);
		upButton.setActionCommand(controller.COMMAND_INTRUN_U_UP);
		upButton.getModel().addChangeListener(this);
		connectedOnly.add(upButton);
		stoppedOnly.add(upButton);
		cons.gridy = 4;
		cons.gridx = 1;
		tabletTab.add(upButton, cons);

		downButton = new JButton(MSG_UNAME+" "+MSG_BUTTON_DOWN);
		downButton.setActionCommand(controller.COMMAND_INTRUN_U_DOWN);
		downButton.getModel().addChangeListener(this);
		connectedOnly.add(downButton);
		stoppedOnly.add(downButton);
		cons.gridy = 5;
		cons.gridx = 1;
		tabletTab.add(downButton, cons);

		upButton = new JButton(MSG_VNAME+" "+MSG_BUTTON_UP);
		upButton.setActionCommand(controller.COMMAND_INTRUN_V_UP);
		upButton.getModel().addChangeListener(this);
		connectedOnly.add(upButton);
		stoppedOnly.add(upButton);
		cons.gridy = 4;
		cons.gridx = 3;
		tabletTab.add(upButton, cons);

		downButton = new JButton(MSG_VNAME+" "+MSG_BUTTON_DOWN);
		downButton.setActionCommand(controller.COMMAND_INTRUN_V_DOWN);
		downButton.getModel().addChangeListener(this);
		connectedOnly.add(downButton);
		stoppedOnly.add(downButton);
		cons.gridy = 5;
		cons.gridx = 3;
		tabletTab.add(downButton, cons);

		upButton = new JButton(MSG_WNAME+" "+MSG_BUTTON_UP);
		upButton.setActionCommand(controller.COMMAND_INTRUN_W_UP);
		upButton.getModel().addChangeListener(this);
		connectedOnly.add(upButton);
		stoppedOnly.add(upButton);
		cons.gridy = 4;
		cons.gridx = 5;
		tabletTab.add(upButton, cons);

		downButton = new JButton(MSG_WNAME+" "+MSG_BUTTON_DOWN);
		downButton.setActionCommand(controller.COMMAND_INTRUN_W_DOWN);
		downButton.getModel().addChangeListener(this);
		connectedOnly.add(downButton);
		stoppedOnly.add(downButton);
		cons.gridy = 5;
		cons.gridx = 5;
		tabletTab.add(downButton, cons);

		// chuck button

		chuckButton2 = new JButton(MSG_BUTTON_OPENCHUCK);
		chuckButton2.setActionCommand(controller.COMMAND_OPENCHUCK);
		chuckButton2.addActionListener(controller);
		connectedOnly.add(chuckButton2);
		cons.gridy = 7;
		cons.gridwidth = 5;
		cons.gridx = 1;
		tabletTab.add(chuckButton2, cons);
		cons.gridwidth = 1;

		// separators

		cons.weightx = cons.weighty = 0.1;
		cons.gridx = 0;
		cons.gridy = 0;
		tabletTab.add(new Panel(), cons);
		cons.gridx = 2;
		cons.gridy = 3;
		tabletTab.add(new Panel(), cons);
		cons.gridx = 4;
		cons.gridy = 6;
		tabletTab.add(new Panel(), cons);
		cons.gridx = 6;
		cons.gridy = 8;
		tabletTab.add(new Panel(), cons);
		cons.weightx = cons.weighty = 1;

		tabs.add(MSG_TAB_TABLET, tabletTab);

		// drawing area (test only?) ///////////////////////////////////////////

		var canvasTab = new Panel();
		canvasTab.setLayout(new GridBagLayout());
		cons = new GridBagConstraints();
		cons.fill = GridBagConstraints.HORIZONTAL;

		canvas = new TestDrawCanvas(this);
		cons.gridy = 0;
		cons.gridx = 0;
		cons.gridwidth = 4;
		canvasTab.add(canvas, cons);
		cons.gridwidth = 1;

		var backspaceButton = new JButton(MSG_BUTTON_BACKSPACE);
		backspaceButton.addActionListener(controller);
		backspaceButton.setActionCommand(controller.COMMAND_BACKSPACE);
//		connectedOnly.add(backspaceButton);
		stoppedOnly.add(backspaceButton);
		cons.gridy = 1;
		cons.gridx = 0;
		canvasTab.add(backspaceButton, cons);

		var clearButton = new JButton(MSG_BUTTON_CLEAR);
		clearButton.addActionListener(controller);
		clearButton.setActionCommand(controller.COMMAND_CLEAR);
//		connectedOnly.add(clearButton);
		stoppedOnly.add(clearButton);
		cons.gridx = 1;
		canvasTab.add(clearButton, cons);

		canvasScale = new JTextField("1", 4);
		stoppedOnly.add(canvasScale);
		cons.gridx = 2;
		canvasTab.add(canvasScale, cons);
		cons.gridx = 3;
		canvasTab.add(new JLabel(MSG_SCALELABEL), cons);

		tabs.add(MSG_TAB_CANVAS, canvasTab);

		// status bar //////////////////////////////////////////////////////////

		statusBar = new Label("");
		topConstraints.weighty = 0;
		add(statusBar, topConstraints);
		topConstraints.weighty = 1;
		topConstraints.gridy++;

		// motion control //////////////////////////////////////////////////////

		buttons = new Panel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

		startButton = new Button(MSG_BUTTON_START);
		startButton.addActionListener(controller);
		startButton.setActionCommand(controller.COMMAND_START);
		connectedOnly.add(startButton);
		buttons.add(startButton);

		defaultSpeed = new TextField("3200", 4);
		buttons.add(defaultSpeed);
		stoppedOnly.add(defaultSpeed);
		buttons.add(new Label(MSG_SPEEDLABEL));

		var zeroButton = new Button(MSG_BUTTON_ZERO);
		zeroButton.addActionListener(controller);
		zeroButton.setActionCommand(controller.COMMAND_ZERO);
		connectedOnly.add(zeroButton);
		stoppedOnly.add(zeroButton);
		buttons.add(zeroButton);

		add(buttons, topConstraints);
		topConstraints.gridy++;

		//////////////////////////////////////////////////////////////////////// 

		setStopped();
		setDisconnected();

		addWindowListener(this);
		setTitle(MSG_TITLE);
		pack();
		setVisible(true);
	}

	private String keyCodeToDebug(int keyCode){
		switch(keyCode){
			case KeyEvent.VK_NUMPAD4:
				return "NUMPAD 4";
			case KeyEvent.VK_NUMPAD6:
				return "NUMPAD 6";
			case KeyEvent.VK_NUMPAD2:
				return "NUMPAD 2";
			case KeyEvent.VK_NUMPAD8:
				return "NUMPAD 8";
			case KeyEvent.VK_NUMPAD9:
				return "NUMPAD 9";
			case KeyEvent.VK_NUMPAD3:
				return "NUMPAD 3";
			case KeyEvent.VK_LEFT:
				return "ARROW LEFT";
			case KeyEvent.VK_RIGHT:
				return "ARROW IGHT";
			case KeyEvent.VK_DOWN:
				return "ARROW DOWN";
			case KeyEvent.VK_UP:
				return "ARROW UP";
			case KeyEvent.VK_PAGE_DOWN:
				return "PAGE DOWN";
			case KeyEvent.VK_PAGE_UP:
				return "PAGE UP";
			default:
				return "<"+keyCode+">";
		}
	}

	// passing user inputs /////////////////////////////////////////////////////

	/**
	 * Parses a String to double, returns NaN when parsing fails
	 */
	private double parseDouble(String text){
		try{
			return Double.parseDouble(text.replace(",", "."));
		}
		catch(Exception ex){
			return Double.NaN;
		}
	}

	/**
	 * Parses a String o double, returns null in place of invalid values.
	 * Used to construct AxisConfig with or without axis limits.
	 */
	private Double string2MaybeNumber(String text){
		double value = parseDouble(text);
		return Double.isFinite(value)? value : null;
	}

	/**
	 * Converts a Double or null to String, with empty String for invalid values.
	 * Used to represent optional axis limits in text fields.
	 */
	private String maybeNumber2String(Double num){
		if((num == null) || num.isNaN())return "";
		else return num.toString();
	}

	public MachineConfig getAxes(){
		MachineConfig last = machineConf;
		machineConf = new MachineConfig(
			new AxisConfig(
				xAxisExists.getState(),
				parseDouble(xAxisRecalc.getText()),
				xAxisInverted.getState(),
				xAxisZeroUp.getState(),
				string2MaybeNumber(xAxisFrom.getText()),
				string2MaybeNumber(xAxisTo.getText()),
				parseDouble(xAxisBacklash.getText())
			),
			new AxisConfig(
				yAxisExists.getState(),
				parseDouble(yAxisRecalc.getText()),
				yAxisInverted.getState(),
				yAxisZeroUp.getState(),
				string2MaybeNumber(yAxisFrom.getText()),
				string2MaybeNumber(yAxisTo.getText()),
				parseDouble(yAxisBacklash.getText())
			),
			new AxisConfig(
				zAxisExists.getState(),
				parseDouble(zAxisRecalc.getText()),
				zAxisInverted.getState(),
				zAxisZeroUp.getState(),
				string2MaybeNumber(zAxisFrom.getText()),
				string2MaybeNumber(zAxisTo.getText()),
				parseDouble(zAxisBacklash.getText())
			),
			new AxisConfig(
				uAxisExists.getState(),
				parseDouble(uAxisRecalc.getText()),
				uAxisInverted.getState(),
				uAxisZeroUp.getState(),
				string2MaybeNumber(uAxisFrom.getText()),
				string2MaybeNumber(uAxisTo.getText()),
				parseDouble(uAxisBacklash.getText())
			),
			new AxisConfig(
				vAxisExists.getState(),
				parseDouble(vAxisRecalc.getText()),
				vAxisInverted.getState(),
				vAxisZeroUp.getState(),
				string2MaybeNumber(vAxisFrom.getText()),
				string2MaybeNumber(vAxisTo.getText()),
				parseDouble(vAxisBacklash.getText())
			),
			new AxisConfig(
				wAxisExists.getState(),
				parseDouble(vAxisRecalc.getText()),
				wAxisInverted.getState(),
				wAxisZeroUp.getState(),
				string2MaybeNumber(vAxisFrom.getText()),
				string2MaybeNumber(vAxisTo.getText()),
				parseDouble(wAxisBacklash.getText())
			),
			getStepRate()
		);

		System.out.println("gui.getAxes:");
		System.out.println("  X "+machineConf.x.exists()+", "+machineConf.x.stepsPerMm()+", "+machineConf.x.inverted()+", "+machineConf.x.zeroUp()+", "+machineConf.x.lowLimit()+", "+machineConf.x.highLimit());
		System.out.println("  Y "+machineConf.y.exists()+", "+machineConf.y.stepsPerMm()+", "+machineConf.y.inverted()+", "+machineConf.y.zeroUp()+", "+machineConf.y.lowLimit()+", "+machineConf.y.highLimit());
		System.out.println("  Z "+machineConf.z.exists()+", "+machineConf.z.stepsPerMm()+", "+machineConf.z.inverted()+", "+machineConf.z.zeroUp()+", "+machineConf.z.lowLimit()+", "+machineConf.z.highLimit());
		System.out.println("  U "+machineConf.u.exists()+", "+machineConf.u.stepsPerMm()+", "+machineConf.u.inverted()+", "+machineConf.u.zeroUp()+", "+machineConf.u.lowLimit()+", "+machineConf.u.highLimit());
		System.out.println("  V "+machineConf.v.exists()+", "+machineConf.v.stepsPerMm()+", "+machineConf.v.inverted()+", "+machineConf.v.zeroUp()+", "+machineConf.v.lowLimit()+", "+machineConf.v.highLimit());
		System.out.println("  W "+machineConf.w.exists()+", "+machineConf.w.stepsPerMm()+", "+machineConf.w.inverted()+", "+machineConf.w.zeroUp()+", "+machineConf.w.lowLimit()+", "+machineConf.w.highLimit());

		boolean[] invalid = new boolean[]{
			(last == null || !last.x.equals(machineConf.x)),
			(last == null || !last.y.equals(machineConf.y)),
			(last == null || !last.z.equals(machineConf.z)),
			(last == null || !last.u.equals(machineConf.u)),
			(last == null || !last.v.equals(machineConf.v)),
			(last == null || !last.w.equals(machineConf.w))
		};
		controller.invalidatePosition(invalid);

		machine.configure(machineConf);
		updateEnabled();

		return machineConf;
	}

	private String roundIfPossible(double value){
		String result = (""+value).replace(",", ".");
		if(result.endsWith(".0"))result = result.substring(0, result.length()-2);
		return result;
	}

	public void setAxes(MachineConfig mc){
		machineConf = mc;

		xAxisExists.setState(mc.x.exists());
		xAxisRecalc.setText(roundIfPossible(mc.x.stepsPerMm()));
		xAxisInverted.setState(mc.x.inverted());
		xAxisZeroUp.setState(mc.x.zeroUp());
		xAxisFrom.setText(maybeNumber2String(mc.x.lowLimit()));
		xAxisTo.setText(maybeNumber2String(mc.x.highLimit()));
		xAxisBacklash.setText(""+mc.x.backlash());

		yAxisExists.setState(mc.y.exists());
		yAxisRecalc.setText(roundIfPossible(mc.y.stepsPerMm()));
		yAxisInverted.setState(mc.y.inverted());
		yAxisZeroUp.setState(mc.y.zeroUp());
		yAxisFrom.setText(maybeNumber2String(mc.y.lowLimit()));
		yAxisTo.setText(maybeNumber2String(mc.y.highLimit()));
		yAxisBacklash.setText(""+mc.y.backlash());

		zAxisExists.setState(mc.z.exists());
		zAxisRecalc.setText(roundIfPossible(mc.z.stepsPerMm()));
		zAxisInverted.setState(mc.z.inverted());
		zAxisZeroUp.setState(mc.z.zeroUp());
		zAxisFrom.setText(maybeNumber2String(mc.z.lowLimit()));
		zAxisTo.setText(maybeNumber2String(mc.z.highLimit()));
		zAxisBacklash.setText(""+mc.z.backlash());

		uAxisExists.setState(mc.u.exists());
		uAxisRecalc.setText(roundIfPossible(mc.u.stepsPerMm()));
		uAxisInverted.setState(mc.u.inverted());
		uAxisZeroUp.setState(mc.u.zeroUp());
		uAxisFrom.setText(maybeNumber2String(mc.u.lowLimit()));
		uAxisTo.setText(maybeNumber2String(mc.u.highLimit()));
		uAxisBacklash.setText(""+mc.u.backlash());

		vAxisExists.setState(mc.v.exists());
		vAxisRecalc.setText(roundIfPossible(mc.v.stepsPerMm()));
		vAxisInverted.setState(mc.v.inverted());
		vAxisZeroUp.setState(mc.v.zeroUp());
		vAxisFrom.setText(maybeNumber2String(mc.v.lowLimit()));
		vAxisTo.setText(maybeNumber2String(mc.v.highLimit()));
		vAxisBacklash.setText(""+mc.v.backlash());

		wAxisExists.setState(mc.w.exists());
		vAxisRecalc.setText(roundIfPossible(mc.w.stepsPerMm()));
		wAxisInverted.setState(mc.w.inverted());
		wAxisZeroUp.setState(mc.w.zeroUp());
		vAxisFrom.setText(maybeNumber2String(mc.w.lowLimit()));
		vAxisTo.setText(maybeNumber2String(mc.w.highLimit()));
		wAxisBacklash.setText(""+mc.w.backlash());

		defaultSpeed.setText(""+mc.stepsPerSecond);

		machine.configure(machineConf);
		updateEnabled();
	}

	public void closeInputFile(){
		if(fileSource != null){
			fileSource.close();
			fileSource = null;
			waitForFileToolPathEnd = true;
		}
		loadFileButton.setText(MSG_BUTTON_LOADFILE);
//		defaultStatus(); // path not yet done, there may be buffered moves remaining
		updateEnabled();
	}

	public void discardInputFile(){
		closeInputFile();
		previewCanvas.clean();
		waitForFileToolPathEnd = false;
		toolPathId = null;
		defaultStatus();
		controller.sendPosition();
		updatePreview();
		updateEnabled();
	}

	public double[][] getPoints(){

		if(fileSource != null){
			System.out.println("sending points from file "+fileSource.getName());
			try{
				double zoom = parseDouble(this.zoom.getText());
				if(!Double.isFinite(zoom) || zoom <= 0)zoom = 1;

				if(fileSource instanceof DxfFileSource){
					double zOff = parseDouble(z2DOffset.getText());
					((DxfFileSource)fileSource).setZ2DOffset(zOff/zoom);
				}

				double[] fileShift = new double[]{
					fileStart[0] - firstFilePoint[0],
					fileStart[1] - firstFilePoint[1],
					fileStart[2] - firstFilePoint[2]
				};

				double[][] points = fileSource.getPoints();
				for(int i=0; i<points[0].length; i++){
					points[0][i] = (points[0][i]+fileShift[0]) * zoom;
					points[1][i] = (points[1][i]+fileShift[1]) * zoom;
					points[2][i] = (points[2][i]+fileShift[2]) * zoom;
				}
				if(fileSource.isXYZ()){
					points = machine.xyzToAxes(points);
				}

				if(fileSource.finished()){
					closeInputFile();
				}

				return points;
			}
			catch(IOException ex){
				warn(MSG_FILE_CANTREAD, "Problem reading file data");
				discardInputFile();
				return new double[6][0];
			}
		}

		else if(!waitForFileToolPathEnd && canvas.remaining() > 1){
			System.out.println("sending points from canvas");
			startToolPath(MSG_INPUTMODE_CANVAS);
			double scale = parseDouble(canvasScale.getText());
			int[] axisIndices = getAxisIndices();
			if(axisIndices.length == 0){
				return new double[6][0];
			}
			else if(axisIndices.length == 1){
				return canvas.getPoints(scale, axisIndices[0], axisIndices[0]);
			}
			else{
				return canvas.getPoints(scale, axisIndices[0], axisIndices[1]);
			}
		}

		else{
			return new double[6][0];
		}
	}

	public boolean readingPathFromFile(){
		return (fileSource == null);
	}

	public double getStepRate(){
		if(defaultSpeed == null){
			return 3200;
		}
		return parseDouble(defaultSpeed.getText());
	}

	// canvas control //////////////////////////////////////////////////////////

	public void backspace(){
		canvas.backspace();
	}

	public void clear(boolean hard){
		canvas.clear(hard);
	}

	// state changes ///////////////////////////////////////////////////////////

	private String portName;

	public void setConnected(String portName){
		connected = true;
		updateEnabled();
		connectButton.setLabel(MSG_BUTTON_DISCONNECT);
		connectButton.setEnabled(true);
		this.portName = portName;
		portLabel.setText(portName);
	}
	public void setDisconnected(){
		connected = false;
		updateEnabled();
		connectButton.setLabel(MSG_BUTTON_FIND);
		connectButton.setEnabled(true);
		portLabel.setText(MSG_NOTCONNECTED);
	}
	public void setSearching(){
		connected = false;
		updateEnabled();
		connectButton.setLabel(MSG_BUTTON_FIND);
		connectButton.setEnabled(false);
		portLabel.setText(MSG_SEARCHING);
	}

	public void setRunning(){
		setRunning(MODE_RUN);
	}
	public void setRunning(String mode){
		running = true;
		updateEnabled();
		canvas.enabled = false;
		startButton.setLabel(MSG_BUTTON_STOP);
//		portLabel.setText(
//			  (mode == MODE_LIMIT)? MSG_TESTINGLIMITS
//			: (mode == MODE_ZERO)?  MSG_GOINGTOZERO
//			:                       MSG_RUNNING);
		portLabel.setText(String.format(MSG_RUNNING, portName));
	}
	public void setStopped(){
		running = false;
		updateEnabled();
		canvas.enabled = true;
		startButton.setLabel(MSG_BUTTON_START);
		portLabel.setText(connected? portName : MSG_NOTCONNECTED);
	}

	public void setChuck(boolean open){
		chuckButton1.setText(open? MSG_BUTTON_CLOSECHUCK : MSG_BUTTON_OPENCHUCK);
		chuckButton1.setActionCommand(open? controller.COMMAND_CLOSECHUCK : controller.COMMAND_OPENCHUCK);
		chuckButton2.setText(open? MSG_BUTTON_CLOSECHUCK : MSG_BUTTON_OPENCHUCK);
		chuckButton2.setActionCommand(open? controller.COMMAND_CLOSECHUCK : controller.COMMAND_OPENCHUCK);
	}

	public double getSpindleSpeed(){
		return parseDouble(spindleInput.getText());
	}

	private String toolPathId = null;
	public void startToolPath(String pathId){
		toolPathId = pathId;
		setStatus(toolPathId);
	}
	public void toolPathFinished(){
		toolPathId = null;
		discardInputFile();
		setStatus((canvas.remaining() > 1)? MSG_INPUTMODE_CANVAS : MSG_INPUTMODE_DONE);
	}
	public void canvasUpdated(){
		if(this.toolPathId == null && canvas.remaining() > 1){
			startToolPath(MSG_INPUTMODE_CANVAS);
		}
	}

	private void updateEnabled(){
		if(connected)for(int i=connectedOnly.size()-1; i>=0; i--){
			connectedOnly.get(i).setEnabled(true);
		}
		if(!running)for(int i=stoppedOnly.size()-1; i>=0; i--){
			stoppedOnly.get(i).setEnabled(true);
		}
		if(!connected)for(int i=connectedOnly.size()-1; i>=0; i--){
			connectedOnly.get(i).setEnabled(false);
		}
		if(running)for(int i=stoppedOnly.size()-1; i>=0; i--){
			stoppedOnly.get(i).setEnabled(false);
		}
		if(connected && !running){
			xRunButton.setEnabled(machineConf.x.exists());
			yRunButton.setEnabled(machineConf.y.exists());
			zRunButton.setEnabled(machineConf.z.exists());
			uRunButton.setEnabled(machineConf.u.exists());
			vRunButton.setEnabled(machineConf.v.exists());
			wRunButton.setEnabled(machineConf.w.exists());
		}
		if(zoom != null){
			boolean loadFileEnabled = !waitForFileToolPathEnd;
			loadFileButton.setEnabled(loadFileEnabled);
			boolean fileSettingsEnabled = (fileSource != null) && !fileSource.started();
			zoom.setEnabled(fileSettingsEnabled);
			boolean zOffsetEnabled = fileSettingsEnabled && fileSource.is2D();
			z2DOffset.setEnabled(zOffsetEnabled);
		}
	}

	private void updatePreview(){
		if(previewCanvas != null){
			previewCanvas.setOutline(machine.plotOutline());
			previewCanvas.repaint();
			double[][] bounds = previewCanvas.getPathBounds();

			String min, max;

			min = Double.isFinite(bounds[0][0])? String.format("%.2f", bounds[0][0]) : "?";
			max = Double.isFinite(bounds[0][1])? String.format("%.2f", bounds[0][1]) : "?";
			xRecalcLabel.setText(
				"   "
				+ MSG_XNAME + " "
				+ MSG_AXISFROM + " " + min + " "
				+ MSG_AXISTO + " " + max + " "
				+ MSG_UNITLABEL1
			);

			min = Double.isFinite(bounds[1][0])? String.format("%.2f", bounds[1][0]) : "?";
			max = Double.isFinite(bounds[1][1])? String.format("%.2f", bounds[1][1]) : "?";
			yRecalcLabel.setText(
				"   "
				+ MSG_YNAME + " "
				+ MSG_AXISFROM + " " + min + " "
				+ MSG_AXISTO + " " + max + " "
				+ MSG_UNITLABEL1
			);

			min = Double.isFinite(bounds[2][0])? String.format("%.2f", bounds[2][0]) : "?";
			max = Double.isFinite(bounds[2][1])? String.format("%.2f", bounds[2][1]) : "?";
			zRecalcLabel.setText(
				"   "
				+ MSG_ZNAME + " "
				+ MSG_AXISFROM + " " + min + " "
				+ MSG_AXISTO + " " + max + " "
				+ MSG_UNITLABEL1
			);
		}
	}

	public void setStatus(String message){
		setStatus(message, false);
	}
	public void setStatus(String message, boolean highlight){
		System.out.println("setStatus \""+message+"\""+(highlight? ", highlight" : ""));
		statusBar.setText(message);
		statusBar.setBackground(highlight? Color.YELLOW : null);
	}
	public void defaultStatus(){
		setStatus((toolPathId == null)? "" : toolPathId);
	}

	public void warn(String statusMessage, String logMessage){
		System.err.println(logMessage);
		setStatus(statusMessage, true);
	}

	public void setPosition(double[] position){
		positionX.setText(Double.isNaN(position[0])? MSG_UNKNOWNPOSITION : String.format("%.2f", position[0]));
		positionY.setText(Double.isNaN(position[1])? MSG_UNKNOWNPOSITION : String.format("%.2f", position[1]));
		positionZ.setText(Double.isNaN(position[2])? MSG_UNKNOWNPOSITION : String.format("%.2f", position[2]));
		positionU.setText(Double.isNaN(position[3])? MSG_UNKNOWNPOSITION : String.format("%.2f", position[3]));
		positionV.setText(Double.isNaN(position[4])? MSG_UNKNOWNPOSITION : String.format("%.2f", position[4]));
		if((fileSource != null && !fileSource.started())
			|| (fileSource == null && !waitForFileToolPathEnd)){
			double[] noNaNPosition = new double[]{ // hotfix for YZU machines running with XYZ machine model
				Double.isNaN(position[0])? 0.0 : position[0],
				Double.isNaN(position[1])? 0.0 : position[1],
				Double.isNaN(position[2])? 0.0 : position[2],
				Double.isNaN(position[3])? 0.0 : position[3],
				Double.isNaN(position[4])? 0.0 : position[4],
				Double.isNaN(position[5])? 0.0 : position[5]
			};
			fileStart = machine.axesToXyz(noNaNPosition);
			previewCanvas.setStart(fileStart);
			updatePreview();
		}
	}

	public double[] getManualPosition(){
		return new double[]{
			parseDouble(manualX.getText()),
			parseDouble(manualY.getText()),
			parseDouble(manualZ.getText()),
			parseDouble(manualU.getText()),
			parseDouble(manualV.getText()),
			0
		};
	}

	public boolean manualAbsolute(){
		return (manualMode.getSelectedIndex() == 1);
	}

	public boolean manualLimits(){
		return (manualStopAtLimits.isSelected());
	}

	private String pathToExtension(String path){
		String[] parts = path.split("\\.");
		if(parts.length >= 2 && parts[parts.length-1].length() <= 6){
			return parts[parts.length-1].toUpperCase();
		}
		else return "";
	}

	private String getAxisSignature(){
		String signature = "";
		if(xAxisExists.getState())signature = signature+"X";
		if(yAxisExists.getState())signature = signature+"Y";
		if(zAxisExists.getState())signature = signature+"Z";
		if(uAxisExists.getState())signature = signature+"U";
		if(vAxisExists.getState())signature = signature+"V";
		if(wAxisExists.getState())signature = signature+"W";
		return signature;
	}

	private int[] getAxisIndices(){
		return getAxisIndices(getAxisSignature());
	}

	private int[] getAxisIndices(String signature){
		signature = signature;
		int[] axisIndices = new int[signature.length()];
		for(int i=0; i<axisIndices.length; i++){
			char ax = signature.charAt(i);
			     if(ax == 'X')axisIndices[i] = 0;
			else if(ax == 'Y')axisIndices[i] = 1;
			else if(ax == 'Z')axisIndices[i] = 2;
			else if(ax == 'U')axisIndices[i] = 3;
			else if(ax == 'V')axisIndices[i] = 4;
			else if(ax == 'W')axisIndices[i] = 5;
		}
		return axisIndices;
	}

	private boolean idxExtensionCompatible(String ext){
		String sgn = getAxisSignature();
		if(ext.length() != sgn.length())return false;
		for(int i=sgn.length()-1; i>=0; i--){
			if(ext.indexOf(sgn.charAt(i)) < 0)return false;
		}
		return true;
	}

	private static final int FORMAT_NONE = 0;
	private static final int FORMAT_DXF = 1;
	private static final int FORMAT_IDX = 2;
	private int recognizeExtension(String ext, boolean verbose){

		if(ext.toLowerCase().equals("dxf")){
			return FORMAT_DXF;
		}

		// checking for idx-like extension (.xyz and so on)
		Pattern idxPattern = Pattern.compile("^[XYZUVW]+$");
		if(idxPattern.matcher(ext).find()){
			if(idxExtensionCompatible(ext)){
				return FORMAT_IDX;
			}
			else{
				if(verbose)warn(
					String.format(MSG_FILE_EXTMISMATCH, ext, getAxisSignature()),
					"File extension "+ext+" does not match current axes "+getAxisSignature()
				);
				return FORMAT_NONE;
			}
		}

		if(verbose)warn(
			String.format(MSG_FILE_BADEXT, ext),
			"Unrecognized extension: "+ext
		);
		return FORMAT_NONE;
	}

	private void showFileDialog(){
		FileDialog dialog = new FileDialog(this, MSG_SELECTFILE);
		dialog.setFilenameFilter((File f, String name) -> {
			return (recognizeExtension(pathToExtension(name), false) != FORMAT_NONE);
		});
		dialog.setVisible(true);
		if(dialog.getFile() != null){
			openFile(dialog.getDirectory() + dialog.getFile());
		}
	}

	private void openFile(String filePath){

		// checking again if file extension valid for current machine
		int fileFormat = recognizeExtension(pathToExtension(filePath), true);

		try{ // opening/parsing files tends to throw exceptions

			// opening DXF file
			if(fileFormat == FORMAT_DXF){
				double zOff = parseDouble(z2DOffset.getText());
				fileSource = new DxfFileSource(filePath, zOff);
			}

			// opening IDX file
			else if(fileFormat == FORMAT_IDX){
				int[] fileAxisIndices = getAxisIndices(pathToExtension(filePath));
				fileSource = new IdxFileSource(
					filePath,
					getAxisSignature().length(),
					fileAxisIndices
				);
			}

			preloadFile(fileSource);
			fileSource = fileSource.duplicate();

		}
		catch(Exception ex){
			ex.printStackTrace();
			discardInputFile();
			warn(MSG_FILE_CANTOPEN, "Problem opening file");
		}

		// updating GUI when onpening file succeeded
		if(fileSource != null){
			startToolPath(String.format(MSG_INPUTMODE_FILE, fileSource.getName()));
			loadFileButton.setText(MSG_BUTTON_DISCARDFILE);
			updateEnabled();
		}

	}

	public void preloadFile(FileSource fs){
		previewCanvas.clean();
		try{
			boolean first = true;
			while(!fs.finished()){
				double[][] points = fs.getPoints(true);
				if(!fs.isXYZ()){ // axis-based point source, need to recalculate to XYZ
					points = machine.axesToXyz(points);
				}

				if(first){
					firstFilePoint = new double[]{ points[0][0], points[1][0], points[2][0] };
					first = false;
				}

				System.out.println("Adding "+points.length+"D points");
				previewCanvas.addPoints(points);
			}
		}
		catch(IOException ex){}
		fs.close();
		updatePreview();
	}


	// ChangeListener interface (used to detect tab switching)

	public void stateChanged(ChangeEvent ev){
		Object source = ev.getSource();
		if(source instanceof ButtonModel){ // touchscreen buttons
			ButtonModel model = (ButtonModel) source;
			if(model.isArmed() && !touchButtonActive){
				this.touchButtonMove(model.getActionCommand());
				touchButtonActive = true;
			}
			if(!model.isArmed() && touchButtonActive){
				controller.interactiveStop();
				touchButtonActive = false;
			}
		}
		else{
			getAxes();
			updatePreview();
		}
	}

	public void touchButtonMove(String command){
		if(command == controller.COMMAND_INTRUN_X_UP){
			controller.interactiveMove(0, 1);
		}
		else if(command == controller.COMMAND_INTRUN_X_DOWN){
			controller.interactiveMove(0, -1);
		}
		else if(command == controller.COMMAND_INTRUN_Y_UP){
			controller.interactiveMove(1, 1);
		}
		else if(command == controller.COMMAND_INTRUN_Y_DOWN){
			controller.interactiveMove(1, -1);
		}
		else if(command == controller.COMMAND_INTRUN_Z_UP){
			controller.interactiveMove(2, 1);
		}
		else if(command == controller.COMMAND_INTRUN_Z_DOWN){
			controller.interactiveMove(2, -1);
		}
		else if(command == controller.COMMAND_INTRUN_U_UP){
			controller.interactiveMove(3, 1);
		}
		else if(command == controller.COMMAND_INTRUN_U_DOWN){
			controller.interactiveMove(3, -1);
		}
		else if(command == controller.COMMAND_INTRUN_V_UP){
			controller.interactiveMove(4, 1);
		}
		else if(command == controller.COMMAND_INTRUN_V_DOWN){
			controller.interactiveMove(4, -1);
		}
		else if(command == controller.COMMAND_INTRUN_W_UP){
			controller.interactiveMove(5, 1);
		}
		else if(command == controller.COMMAND_INTRUN_W_DOWN){
			controller.interactiveMove(5, -1);
		}
	}


	// ActionListener interface (used to open file dialog)

	public void actionPerformed(ActionEvent ev){
		String cmd = ev.getActionCommand();
		System.out.println("gui command "+cmd);
		switch(cmd){

			case COMMAND_LOADFILE:
				defaultStatus();
				if(fileSource != null){
					discardInputFile();
				}
				else{
					showFileDialog();
				}
				break;

		}
	}

	// DocumentListener interface (used to update `previewCanvas`)

	public void changedUpdate(DocumentEvent ev){}
	public void insertUpdate(DocumentEvent ev){
		double zoom = parseDouble(this.zoom.getText());
		if(Double.isFinite(zoom) && zoom > 0){
			previewCanvas.setZoom(zoom);
			updatePreview();
		}
	}
	public void removeUpdate(DocumentEvent ev){
		double zoom = parseDouble(this.zoom.getText());
		if(Double.isFinite(zoom) && zoom > 0){
			previewCanvas.setZoom(zoom);
			updatePreview();
		}
	}

	// WindowListener interface (used to close application)

	public void windowActivated(WindowEvent ev){}
	public void windowClosed(WindowEvent ev){}
	public void windowClosing(WindowEvent ev){
		try{
			controller.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_FIRST, controller.COMMAND_KILL));
		}
		finally{
			dispose();
		}
	}
	public void windowDeactivated(WindowEvent ev){}
	public void windowDeiconified(WindowEvent ev){}
	public void windowIconified(WindowEvent ev){}
	public void windowOpened(WindowEvent arg0){}

}

