//
// $Id$

package client.frame;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.WidgetUtil;
import com.threerings.gwt.util.CookieUtil;
import com.threerings.gwt.util.StringUtil;

import com.threerings.msoy.data.all.DeploymentConfig;
import com.threerings.msoy.data.all.LaunchConfig;
import com.threerings.msoy.data.all.VisitorInfo;
import com.threerings.msoy.facebook.gwt.FacebookService;
import com.threerings.msoy.facebook.gwt.FacebookServiceAsync;
import com.threerings.msoy.web.gwt.Args;
import com.threerings.msoy.web.gwt.CookieNames;
import com.threerings.msoy.web.gwt.Embedding;
import com.threerings.msoy.web.gwt.Invitation;
import com.threerings.msoy.web.gwt.MemberCard;
import com.threerings.msoy.web.gwt.Pages;
import com.threerings.msoy.web.gwt.SessionData;
import com.threerings.msoy.web.gwt.WebMemberService;
import com.threerings.msoy.web.gwt.WebMemberServiceAsync;
import com.threerings.msoy.web.gwt.WebUserService;
import com.threerings.msoy.web.gwt.WebUserServiceAsync;

import client.images.frame.FrameImages;
import client.shell.CShell;
import client.shell.Frame;
import client.shell.ScriptSources;
import client.shell.Session;
import client.shell.ShellMessages;
import client.shell.ThemedStylesheets;
import client.ui.BorderedDialog;
import client.util.ArrayUtil;
import client.util.FlashClients;
import client.util.FlashVersion;
import client.util.Link;
import client.util.InfoCallback;
import client.util.NoopAsyncCallback;
import client.util.events.FlashEvent;
import client.util.events.FlashEvents;
import client.util.events.NameChangeEvent;
import client.util.events.ThemeChangeEvent;

/**
 * Handles the outer shell of the Whirled web application. Loads pages into an iframe and also
 * handles displaying the Flash client.
 */
public class FrameEntryPoint
    implements EntryPoint, ValueChangeHandler<String>, Session.Observer, Frame
{
    /**
     * Creates the new frame entry point.
     */
    public FrameEntryPoint ()
    {
        // listen for trophy events to publish to facebook
        TrophyFeeder.listen();
    }

    // from interface EntryPoint
    public void onModuleLoad ()
    {
        // set up our CShell singleton
        CShell.init(this);

        // our main frame never scrolls
        Window.enableScrolling(false);

        // listen for logon/logoff
        Session.addObserver(this);

        // wire ourselves up to the history-based navigation mechanism
        History.addValueChangeHandler(this);
        _currentToken = History.getToken();

        // assign our client mode and app id if the server gave them to us, otherwise use defaults
        _embedding = Embedding.extract(Args.fromHistory(_currentToken));
        CShell.log("Loading module", "embedding", _embedding);

        // load up various JavaScript sources
        ScriptSources.inject(_embedding.appId);

        // initialize our GA handler
        _analytics.init();

        // set up the callbacks that our flash clients can call
        configureCallbacks(this);

        // listen for theme changes (one which will likely be triggered by the didLogon below)
        FlashEvents.addListener(new ThemeChangeEvent.Listener() {
            public void themeChanged (ThemeChangeEvent event) {
                _themeId = event.getGroupId();
                ThemedStylesheets.inject(_themeId);
            }
        });

        // validate our session which will dispatch a didLogon or didLogoff
        Session.validate();

        // create our header
        _header = new FrameHeader(new ClickHandler() {
            public void onClick (ClickEvent event) {
                if (_closeToken != null) {
                    closeContent();
                } else if (CShell.isGuest()) {
                    History.newItem("");
                } else {
                    Link.go(Pages.WORLD, "m" + CShell.getMemberId());
                }
            }
        });

        // create our frame layout
        _layout = Layout.getLayout(_header, _embedding.mode, isFramed(), new ClickHandler() {
            public void onClick (ClickEvent event) {
                // put the client in in minimized state
                String args = "memberHome=" + CShell.getMemberId() + "&mini=true";
                _closeToken = Pages.WORLD.makeToken("h");
                if (_bar != null) {
                    _bar.setCloseVisible(true);
                }
                WorldClient.displayFlash(args, _layout.getClientProvider());
            }
        });

        // clear out the loading HTML so we can display a browser warning or load Whirled
        DOM.setInnerHTML(RootPanel.get(LOADING).getElement(), "");

        // If the browser is unsupported, hide the page (still being built) and show a warning.
        ClickHandler continueClicked = new ClickHandler() {
            public void onClick (ClickEvent event) {
                // close the warning and show the page if the visitor choose to continue
                RootPanel.get(LOADING).clear();
                RootPanel.get(LOADING).setVisible(false);
            }
        };
        Widget warningDialog = BrowserTest.getWarningDialog(continueClicked);
        if (warningDialog != null) {
            RootPanel.get(LOADING).add(warningDialog);
        } else {
            RootPanel.get(LOADING).clear();
            RootPanel.get(LOADING).setVisible(false);
        }
    }

    // from interface ValueChangeHandler
    public void onValueChange (ValueChangeEvent<String> event)
    {
        setToken(event.getValue());
    }

    public void setToken (String token)
    {
        _prevToken = _currentToken;
        _currentToken = token;

        Pages page;
        Args args;
        try {
            page = Pages.fromHistory(token);
            args = Args.fromHistory(token);
        } catch (Exception e) {
            // on bogus or missing URLs, go to landing page for guests or world-places for members
            if (CShell.isGuest()) {
                page = Pages.LANDING;
                args = Args.fromToken("");
            } else {
                page = Pages.WORLD;
                args = Args.fromToken("places");
            }
        }

        // scrub any cookie-like arguments
        Embedding.extract(args);

        CShell.log("Displaying page", "page", page, "args", args);

        // do some special processing if this is an invitation link
        if (page == Pages.ME && args.get(0, "").equals("i") && CShell.isGuest()) {
            // load up the invitation information and save it
            String inviteId = args.get(1, "");
            if (_activeInvite == null || !_activeInvite.inviteId.equals(inviteId)) {
                _membersvc.getInvitation(inviteId, true, new InfoCallback<Invitation>() {
                    public void onSuccess (Invitation invite) {
                        _activeInvite = invite;
                    }
                });
            }
            // and send them to the landing page
            Link.go(Pages.LANDING, "");
            return;
        }

        // if we have no account cookie (which means we've seen someone on this computer before),
        // force the creation of our visitor info because we're very probably a real new user
        boolean newUser = StringUtil.isBlank(CookieUtil.get(CookieNames.WHO));
        if (newUser) {
            getVisitorInfo(); // creates a visitorId and reports it
        }

        // recreate the page token which we'll pass through to the page (or if it's being loaded
        // for the first time, it will request in a moment with a call to getPageToken)
        _pageToken = args.recompose(0).toToken();

        // replace the page if necessary
        if (_page != page || _page == Pages.WORLD) {
            setPage(page);

        } else {
            // reset our navigation as we're not changing pages but need to give the current page a
            // fresh subnavigation palette
            if (_bar != null) {
                _bar.resetNav();
            }
            _pageFrame.setToken(_pageToken);
            WorldClient.contentChanged(_page, _pageToken);
        }

        // report the page visit
        reportPageVisit(page, args);
    }

    // from interface Session.Observer
    public void didLogon (SessionData data)
    {
        // update the world client to relogin (this will NOOP if we're logging in now because Flash
        // just told us to do so)
        WorldClient.didLogon(data.creds);

        // if they just registered, reboot the flash client (which will put them back where they
        // are but logged in as their newly registered self)
        if (FlashClients.clientExists() && data.group != SessionData.Group.NONE) {
            rebootFlashClient();
        }

        // now that we know we're a member, we can add our "open home in minimized mode" icon
        // (which may get immediately removed if we're going directly into the world)...
        _layout.addNoClientIcon();

        if (data.group != SessionData.Group.NONE) {
            Link.go(Pages.PEEPLESS, "confprof"); // send them to configure profile
        } else if (isHeaderless() || (_page == Pages.ACCOUNT && _prevToken.equals(""))) {
            Link.go(Pages.WORLD, "places");
        } else if (_page == Pages.ACCOUNT) {
            History.back(); // go back to where we were
        } else if (_page != null) {
            setPage(_page); // reloads the current page
        } else {
            setToken(_currentToken);
        }
    }

    // from interface Session.Observer
    public void didLogoff ()
    {
        // clear out any current page
        _page = null;
        // reload the current page (preserving our previous page token)
        String prevToken = _prevToken;
        setToken(_currentToken);
        _prevToken = prevToken;
        // close the Flash client if it's open
        closeClient(true);
    }

    // from interface Frame
    public void setTitle (String title)
    {
        Window.setTitle(title == null ? _cmsgs.bareTitle() : _cmsgs.windowTitle(title));
        if (title != null && _bar != null) {
            _bar.setTitle(title);
        }
    }

    // from interface Frame
    public void addNavLink (String label, Pages page, Args args, int position)
    {
        _bar.addContextLink(label, page, args, position);
        _layout.updateTitleBarHeight();
    }

    // from interface Frame
    public void navigateTo (String token)
    {
        if (!token.equals(_currentToken)) {
            History.newItem(token);
        }
    }

    // from interface Frame
    public void navigateReplace (String token)
    {
        // mysteriously, if we go back() and then newItem() our current location, nothing happens
        // at all, no history changed event, no browser navigation, nothing; I think this might
        // have to do with some of the weird-ass timer based hackery that GWT has to do to make the
        // whole browser history thing work at all
        if (token.equals(_currentToken)) {
            setToken(_currentToken);
        } else {
            History.back();
            History.newItem(token);
        }
    }

    // from interface Frame
    public void closeClient ()
    {
        closeClient(false);
    }

    // from interface Frame
    public void closeContent ()
    {
        // clear out the content
        clearContent(true);

        // restore the client's URL
        if (_closeToken != null) {
            History.newItem(_closeToken);
        }
    }

    // from interface Frame
    public void showDialog (String title, Widget dialog)
    {
        // remove any existing content
        clearDialog();

        _dialog = new BorderedDialog(false, false, false) {
            @Override protected void onClosed (boolean autoClosed) {
                _dialog = null;
            }
        };
        _dialog.setHeaderTitle(title);
        _dialog.setContents(dialog);
        _dialog.show();
    }

    // from interface Frame
    public void clearDialog ()
    {
        if (_dialog != null) {
            _dialog.hide();
        }
    }

    // from interface Frame
    public void dispatchEvent (FlashEvent event)
    {
        // dispatch the event locally
        FlashEvents.internalDispatchEvent(event);

        // forward the event to our page frame
        if (_pageFrame != null) {
            _pageFrame.forwardEvent(event);
        }
    }

    // from interface Frame
    public void dispatchDidLogon (SessionData data)
    {
        Session.didLogon(data);
    }

    // from interface Frame
    public void logoff ()
    {
        Session.didLogoff();
    }

    // from interface Frame
    public void emailUpdated (String address, boolean validated)
    {
        Session.emailUpdated(address, validated);
    }

    // from interface Frame
    public String md5hex (String text)
    {
        return nmd5hex(text);
    }

    // from interface Frame
    public String checkFlashVersion (int width, int height)
    {
        return FlashVersion.checkFlashVersion(width, height);
    }

    // from interface Frame
    public Invitation getActiveInvitation ()
    {
        return _activeInvite;
    }

    // from interface Frame
    public VisitorInfo getVisitorInfo ()
    {
        return Session.frameGetVisitorInfo();
    }

    // from interface Frame
    public void reportTestAction (String test, String action)
    {
        CShell.log("Reporting test action", "test", test, "action", action);
        _membersvc.trackTestAction(test, action, getVisitorInfo(), new NoopAsyncCallback());
    }

    // from interface Frame
    public int getAppId ()
    {
        return _embedding.appId;
    }

    // from interface Frame
    public Embedding getEmbedding ()
    {
        return _embedding;
    }

    // from interface Frame
    public boolean isHeaderless ()
    {
        return (_page != null) && (_page.getTab() == null);
    }

    // from interface Frame
    public void openBottomFrame (String token)
    {
        CShell.log("Opening bottom frame", "token", token);
        Pages page = Pages.fromHistory(token);
        Args args = Args.fromHistory(token);
        String bottomFrameToken = args.recompose(0).toToken();
        if (!bottomFrameToken.equals(_bottomFrameToken)) {
            _bottomFrame = new PageFrame(page, BOTTOM_FRAME_ID);
            _bottomFrameToken = bottomFrameToken;
            _layout.setBottomContent(_bottomFrame);
        }
    }

    // from interface Frame
    public int getThemeId ()
    {
        return _themeId;
    }

    protected void setPage (Pages page)
    {
        // clear out any old content
        clearContent(page == Pages.WORLD);

        // clear out any lingering dialog content
        clearDialog();

        // show the header for pages that report a tab of which they are a part
        _header.selectTab(page.getTab());

        // make a note of our current page
        _page = page;

        // if we're displaying a world page, that's special
        if (page == Pages.WORLD) {
            WorldClient.contentCleared();
            displayWorld(_pageToken);
            return;
        }

        // tell the flash client we're minimizing it
        WorldClient.setMinimized(true);

        // create our page frame
        _pageFrame = new PageFrame(_page, MAIN_FRAME_ID);

        // if we're on a headerless page or we only support one screen, we need to close the client
        if (isHeaderless() || _embedding.mode.isMonoscreen()) {
            closeClient();
        }

        if (isHeaderless()) {
            _bar = null;

        } else {
            _bar = TitleBar.create(_layout, page.getTab(), _closeContent);
            _bar.setCloseVisible(FlashClients.clientExists());
        }

        _layout.setContent(_bar, _pageFrame);
        _bottomFrame = null;
        _bottomFrameToken = "";

        // let the flash client know we are changing pages
        WorldClient.contentChanged(_page, _pageToken);
    }

    protected void clearContent (boolean restoreClient)
    {
        if (_layout.hasContent()) {
            _layout.closeContent(restoreClient);

            // restore the title to the last thing flash asked for
            setTitle(_closeTitle);
        }

        // let the Flash client know that it's being unminimized or to start unminimized
        WorldClient.setMinimized(false);

        _pageFrame = null;
        _bottomFrame = null;
        _bottomFrameToken = "";
        if (!_layout.alwaysShowsTitleBar()) {
            _bar = null;
        }
    }

    protected void closeClient (boolean didLogoff)
    {
        WorldClient.clientWillClose();
        _closeToken = null;
        _closeTitle = null;

        if (_bar != null) {
            _bar.setCloseVisible(false);
        }

        if (_layout.closeClient()) {
            // if we just logged off, go to the logoff page
            if (didLogoff) {
                Link.go(Pages.ACCOUNT, "logoff");

            // if we're on a "world" page, go to a landing page
            } else if (_currentToken != null &&
                       (_currentToken.startsWith(Pages.WORLD.makeToken()) ||
                        _currentToken.equals(""))) {
                if (_currentToken.indexOf("game") != -1) {
                    // if we were in a game, go to the games page
                    Link.go(Pages.GAMES, "");
                } else if (CShell.isGuest()) {
                    // if we're a guest, go to the rooms page
                    Link.go(Pages.ROOMS, "");
                } else {
                    // otherwise go to the ME page
                    Link.go(Pages.ME, "");
                }
            }
        }
    }

    protected void displayWorld (String pageToken)
    {
        Args args = Args.fromToken(pageToken);

        String action = args.get(0, "");
        if (action.startsWith("s")) {
            String sceneId = action.substring(1);
            if (args.getArgCount() <= 1) {
                displayWorldClient("sceneId=" + sceneId, null);
            } else {
                // if we have sNN-extra-args we want the close button to use just "sNN"
                displayWorldClient("sceneId=" + sceneId + "&page=" + args.get(1, "") +
                                   "&args=" + args.recompose(2),
                                   Pages.WORLD.makeToken("s" + sceneId));
            }

        } else if (action.equals("game")) {
            // display a game lobby or enter a game (action_gameId_otherid1_token_otherid2)
            displayGame(args.get(1, ""), args.get(2, 0), args.get(3, 0), args.get(4, ""),
                        args.get(5, 0));

        } else if (action.equals("fbgame")) {
            // we're entering a chromeless facebook game (fbgame_gameId_fbid_fbtok)
            _facebookId = args.get(2, "");
            _facebookSession = args.get(3, "");
            FlashClients.setChromeless(true);
            displayGame("p", args.get(1, 0), 0, "", 0);

        } else if (action.equals("tour")) {
            displayWorldClient("tour=true", null);

        } else if (action.startsWith("g")) {
            // go to a specific group's scene group
            displayWorldClient("groupHome=" + action.substring(1), null);

        } else if (action.startsWith("m")) {
            // go to a specific member's home
            displayWorldClient("memberHome=" + action.substring(1), null);

        } else if (action.startsWith("c")) {
            // join a group chat
            displayWorldClient("groupChat=" + action.substring(1), null);

        } else if (action.equals("h")) {
            // go to our home
            displayWorldClient("memberHome=" + CShell.getMemberId(), null);

        } else if (action.equals("hplaces")) {
            // just logon and show the myplaces dialog, don't go anywhere
            displayWorldClient("myplaces=true", null);

        } else { // (action == "places" or anything else)
            // just logon and go home for now
            displayWorldClient("memberHome=" + CShell.getMemberId(), null);
        }
    }

    /**
     * Displays a world client for viewing a scene.
     */
    protected void displayWorldClient (String args, String closeToken)
    {
        displayWorldClient(args, closeToken, null);
    }

    /**
     * Displays a world client for playing a game.
     */
    protected void displayWorldClient (String args, String closeToken, LaunchConfig game)
    {
        // note the current history token so that we can restore it if needed
        _closeToken = (closeToken == null) ? _currentToken : closeToken;

        // finally actually display the client
        WorldClient.displayFlash(args, _layout.getClientProvider());

        TitleBar bar = TitleBar.createClient(_layout, game);
        if (bar != null) {
            _bar = bar;
            _bar.setCloseVisible(!_embedding.mode.isMonoscreen());
            _layout.setTitleBar(_bar);
        }
    }

    protected void displayGame (final String action, int gameId, final int otherId1,
                                final String token, final int otherId2)
    {
        // load up the information needed to launch the game
        _usersvc.loadLaunchConfig(gameId, new InfoCallback<LaunchConfig>() {
            public void onSuccess (LaunchConfig result) {
                launchGame(result, action, otherId1, token, otherId2);
            }
        });
        if (_embedding.mode.isFacebookGames()) {
            openBottomFrame(Pages.FACEBOOK.makeToken("game", gameId));
        }
    }

    protected void launchGame (final LaunchConfig config, String action, final int otherId1,
                               String token, final int otherId2)
    {
        // configure our world client with a default host and port in case we're first to the party
        WorldClient.setDefaultServer(config.groupServer, config.groupPort);

        // sanitize our token
        token = (token == null) ? "" : token;

        String args;
        switch (config.type) {
        case LaunchConfig.FLASH_IN_WORLD:
            args = "worldGame=" + config.gameId;
            if (action.equals("j")) {
                args += "&inviteToken=" + token + "&inviterMemberId=" + otherId1 +
                    "&gameRoomId=" + otherId2;
            } else {
                args += "&gameRoomId=" + config.sceneId;
            }
            displayWorldClient(args, null, config);
            break;

        case LaunchConfig.FLASH_LOBBIED:
            String hostPort = "&ghost=" + config.gameServer + "&gport=" + config.gamePort;
            args = "gameId=" + config.gameId;

            // "g" means we're going right into an already running game
            if (action.equals("g")) {
                args += "&gameOid=" + otherId1;

            // "j" is from a game invite
            } else if (action.equals("j")) {
                args += "&inviteToken=" + token + "&inviterMemberId=" + otherId1;

            // everything else ("p" and "i" and legacy codes) means 'play now'
            } else if (otherId1 != 0) {
                args += "&playerId=" + otherId1;
            }
            displayWorldClient(args + hostPort, null, config);
            break;

        case LaunchConfig.JAVA_FLASH_LOBBIED:
        case LaunchConfig.JAVA_SELF_LOBBIED:
            if (config.type == LaunchConfig.JAVA_FLASH_LOBBIED && otherId1 <= 0) {
                displayWorldClient("gameId=" + config.gameId, null, config);

            } else {
                // clear out the client as we're going into Java land
                closeClient();

                // prepare a command to be invoked once we know Java is loaded
                _javaReadyCommand = new Command() {
                    public void execute () {
                        displayJava(config, otherId1);
                    }
                };

                // stick up a loading message and the HowdyPardner Java applet
                FlowPanel bits = new FlowPanel();
                bits.setStyleName("javaLoading");
                bits.add(new Label("Loading game..."));

                String hpath = "/clients/" + DeploymentConfig.version + "/howdy.jar";
                bits.add(WidgetUtil.createApplet("game", config.getGameURL(hpath),
                                                 "com.threerings.msoy.client.HowdyPardner",
                                                 "100", "10", true, new String[0]));
                // TODO
                // setContent(bits);
            }
            break;

//         case LaunchConfig.FLASH_SOLO:
//             setFlashContent(
//                     config.name, FlashClients.createSoloGameDefinition(config.clientMediaPath));
//             break;

//         case LaunchConfig.JAVA_SOLO:
//             setContent(config.name, new Label("Not yet supported"));
//             break;

        default:
            CShell.log("Requested to display unsupported game type " + config.type + ".");
            break;
        }
    }

    /**
     * Handles a variety of methods called by our iframed page.
     */
    protected String[] frameCall (String callStr, String frameId, String[] args)
    {
        Calls call = Enum.valueOf(Calls.class, callStr);
        switch (call) {
        case SET_TITLE:
            // only the main frame can set the title
            if (MAIN_FRAME_ID.equals(frameId)) {
                setTitle(args[0]);
            }
            return null;
        case ADD_NAV_LINK:
            addNavLink(args[0], Enum.valueOf(Pages.class, args[1]), Args.fromToken(args[2]),
                       Integer.parseInt(args[3]));
            return null;
        case NAVIGATE_TO:
            // TODO: can the bottom frame navigate itself?
            navigateTo(args[0]);
            return null;
        case NAVIGATE_REPLACE:
            // TODO: bottom frame navigation
            if (MAIN_FRAME_ID.equals(frameId)) {
                navigateReplace(args[0]);
            }
            return null;
        case CLOSE_CLIENT:
            closeClient();
            return null;
        case CLOSE_CONTENT:
            closeContent();
            return null;
        case DID_LOGON:
            dispatchDidLogon(SessionData.unflatten(ArrayUtil.toIterator(args)));
            return null;
        case LOGOFF:
            logoff();
            return null;
        case EMAIL_UPDATED:
            emailUpdated(args[0], Boolean.parseBoolean(args[1]));
            return null;
        case GET_WEB_CREDS:
            return (CShell.creds == null) ? null : CShell.creds.flatten().toArray(new String[0]);
        case GET_PAGE_TOKEN:
            if (MAIN_FRAME_ID.equals(frameId)) {
                return new String[] { _pageToken };
            } else if (BOTTOM_FRAME_ID.equals(frameId)) {
                return new String[] { _bottomFrameToken };
            } else {
                return null;
            }
        case GET_MD5:
            return new String[] { nmd5hex(args[0]) };
        case CHECK_FLASH_VERSION:
            return new String[] {
                checkFlashVersion(Integer.valueOf(args[0]), Integer.valueOf(args[1]))
            };
        case GET_ACTIVE_INVITE:
            return _activeInvite == null ? null : _activeInvite.flatten().toArray(new String[0]);
        case GET_VISITOR_INFO:
            return getVisitorInfo().flatten().toArray(new String[0]);
        case TEST_ACTION:
            reportTestAction(args[0], args[1]);
            return null;
        case GET_EMBEDDING:
            return _embedding.flatten();
        case IS_HEADERLESS:
            return new String[] { String.valueOf(isHeaderless()) };
        case OPEN_BOTTOM_FRAME:
            openBottomFrame(args[0]);
            return null;
        case GET_THEME_ID:
            return new String[] { String.valueOf(getThemeId()) };
        }
        CShell.log("Got unknown frameCall request [call=" + call + "].");
        return null; // not reached
    }

    protected void deferredCloseClient ()
    {
        DeferredCommand.addCommand(new Command() {
            public void execute () {
                closeClient();
            }
        });
    }

    /**
     * Called when Flash or our inner Page frame wants us to dispatch an event.
     */
    protected void triggerEvent (String eventName, JavaScriptObject args)
    {
        FlashEvent event = FlashEvents.createEvent(eventName, args);
        if (event != null) {
            dispatchEvent(event);
        }
    }

    protected void displayJava (LaunchConfig config, int gameOid)
    {
//         String[] args = new String[] {
//             "game_id", "" + config.gameId, "game_oid", "" + gameOid,
//             "server", config.gameServer, "port", "" + config.gamePort,
//             "authtoken", (CWorld.ident == null) ? "" : CWorld.ident.token };
//         String gjpath = "/clients/" + DeploymentConfig.version + "/" +
//             (config.lwjgl ? "lwjgl-" : "") + "game-client.jar";
//         WorldClient.displayJava(
//             WidgetUtil.createApplet(
//                 // here we explicitly talk directly to our game server (not via the public facing
//                 // URL which is a virtual IP) so that Java's security policy works
//                 "game", config.getGameURL(gjpath) + "," + config.getGameURL(config.clientMediaPath),
//                 "com.threerings.msoy.game.client." + (config.lwjgl ? "LWJGL" : "") + "GameApplet",
//                 // TODO: allow games to specify their dimensions in their config
//                 "100%", "600", false, args));
    }

    protected void javaReady ()
    {
        if (_javaReadyCommand != null) {
            DeferredCommand.addCommand(_javaReadyCommand);
            _javaReadyCommand = null;
        }
    }

    protected String getVisitorId ()
    {
        return getVisitorInfo().id;
    }

    protected String getFacebookId ()
    {
        return _facebookId;
    }

    protected String getFacebookSession ()
    {
        return _facebookSession;
    }

    protected void setTitleFromFlash (String title)
    {
        // if we're displaying content currently, don't let flash mess with the title
        if (!_layout.hasContent()) {
            setTitle(title);
        }
        _closeTitle = title;
    }

    protected void setPermaguestInfo (String name, String token)
    {
        // the server has created a permaguest account for us via flash, store the cookies
        CShell.log("Got permaguest info from flash", "name", name, "token", token);
        Session.conveyLoginFromFlash(token);
    }

    protected void refreshDisplayName ()
    {
        _membersvc.getMemberCard(CShell.getMemberId(), new AsyncCallback<MemberCard>() {
            public void onFailure (Throwable caught) {
                // nada
            }
            public void onSuccess (MemberCard result) {
                if (result != null) {
                    dispatchEvent(new NameChangeEvent(result.name.toString()));
                }
            }
        });
    }

    protected void rebootFlashClient ()
    {
        WorldClient.rebootFlash(_layout.getClientProvider());
    }

    protected void reportPageVisit (Pages page, Args args)
    {
        // convert the page to a url
        String url = args.toPath(page);

        // report it to Google Analytics
        _analytics.report(url);

        // and to Kontagent if we are in a Facebook mode
        if (_embedding.mode.isFacebook()) {
            // TODO: we might be able to use the recommended "client pixel" page reporting if the
            // KAPI secret is not required (question posted to forums). If so, then we'd need to
            // grab the FBID from somewhere, perhaps in the SessionData (superseding the
            // #world-fbgame way of doing it), and build a URL here and poke it into an Image in
            // the title bar or somewhere.
            _fbsvc.trackPageRequest(_embedding.appId, url, new NoopAsyncCallback());
        }
    }

    /**
     * Configures top-level functions that can be called by Flash or an iframed
     * {@link client.shell.Page}.
     */
    protected static native void configureCallbacks (FrameEntryPoint entry) /*-{
        $wnd.onunload = function (event) {
            var client = $doc.getElementById("asclient");
            if (client) {
                client.onUnload();
            }
            return true;
        };
        $wnd.frameCall = function (action, pageFrameId, args) {
            return entry.@client.frame.FrameEntryPoint::frameCall(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)(action, pageFrameId, args);
        };
        $wnd.helloWhirled = function () {
             return true;
        };
        $wnd.getClientMode = function () {
            var emb = entry.@client.frame.FrameEntryPoint::getEmbedding()();
            var mode = emb.@com.threerings.msoy.web.gwt.Embedding::mode;
            return mode.@java.lang.Object::toString()();
        }
        $wnd.setWindowTitle = function (title) {
            entry.@client.frame.FrameEntryPoint::setTitleFromFlash(Ljava/lang/String;)(title);
        };
        $wnd.displayPage = function (page, args) {
            @client.util.Link::goFromFlash(Ljava/lang/String;Ljava/lang/String;)(page, args);
        };
        $wnd.clearClient = function () {
             entry.@client.frame.FrameEntryPoint::deferredCloseClient()();
        };
        $wnd.getVisitorId = function () {
             return entry.@client.frame.FrameEntryPoint::getVisitorId()();
        };
        // anoyingly these have to be specified separately, ActionScript chokes on String[]
        $wnd.getFacebookId = function () {
             return entry.@client.frame.FrameEntryPoint::getFacebookId()();
        };
        $wnd.getFacebookSession = function () {
             return entry.@client.frame.FrameEntryPoint::getFacebookSession()();
        };
        $wnd.triggerFlashEvent = function (eventName, args) {
            entry.@client.frame.FrameEntryPoint::triggerEvent(Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;)(eventName, args);
        }
        $wnd.howdyPardner = function () {
            entry.@client.frame.FrameEntryPoint::javaReady()();
        }
        $wnd.setPermaguestInfo = function (name, token) {
            entry.@client.frame.FrameEntryPoint::setPermaguestInfo(Ljava/lang/String;Ljava/lang/String;)(name, token);
        }
        $wnd.refreshDisplayName = function () {
            entry.@client.frame.FrameEntryPoint::refreshDisplayName()();
        }
        $wnd.rebootFlashClient = function () {
            entry.@client.frame.FrameEntryPoint::rebootFlashClient()();
        }
    }-*/;

    /** MD5 hashes the supplied text and returns the hex encoded hash value. */
    public native static String nmd5hex (String text) /*-{
        return $wnd.hex_md5(text);
    }-*/;

    /**
     * Checks if the current web document resides in a frame.
     */
    protected native static boolean isFramed () /*-{
        return $wnd.top != $wnd;
    }-*/;

    protected Pages _page;
    protected String _currentToken = "", _prevToken = "";
    protected String _pageToken = "", _bottomFrameToken = "";
    protected String _closeToken, _closeTitle;
    protected String _facebookId, _facebookSession;
    protected int _themeId;

    protected Embedding _embedding;
    protected FrameHeader _header;
    protected Layout _layout;
    protected TitleBar _bar;
    protected PageFrame _pageFrame;
    protected PageFrame _bottomFrame;
    protected BorderedDialog _dialog;

    /** If the user arrived via an invitation, we'll store that here during their session. */
    protected Invitation _activeInvite;

    /** Used to talk to Google Analytics. */
    protected Analytics _analytics = new Analytics();

    /** A command to be run when Java reports readiness. */
    protected Command _javaReadyCommand;

    protected ClickHandler _closeContent = new ClickHandler() {
        @Override public void onClick (ClickEvent event) {
            closeContent();
        }
    };

    protected static final ShellMessages _cmsgs = GWT.create(ShellMessages.class);
    protected static final FrameImages _images = (FrameImages)GWT.create(FrameImages.class);
    protected static final WebMemberServiceAsync _membersvc = GWT.create(WebMemberService.class);
    protected static final WebUserServiceAsync _usersvc = GWT.create(WebUserService.class);
    protected static final FacebookServiceAsync _fbsvc = GWT.create(FacebookService.class);

    // constants for our top-level elements
    protected static final String PAGE = "page";
    protected static final String LOADING = "loading";
    protected static final String MAIN_FRAME_ID = "main";
    protected static final String BOTTOM_FRAME_ID = "bottom";

    /** This vector string represents an email invite */
    protected static final String EMAIL_VECTOR = "emailInvite";
}
