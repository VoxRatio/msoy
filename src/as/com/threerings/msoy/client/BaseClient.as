//
// $Id$

package com.threerings.msoy.client {

import flash.display.DisplayObject;
import flash.display.Stage;

import flash.external.ExternalInterface;
import flash.system.Security;

import mx.resources.ResourceBundle;

import com.adobe.crypto.MD5;

import com.threerings.util.Name;
import com.threerings.util.ResultAdapter;
import com.threerings.util.StringUtil;

import com.threerings.presents.client.Client;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.dobj.DSet;

import com.threerings.presents.dobj.DObjectManager;

import com.threerings.presents.net.BootstrapData;

import com.threerings.presents.data.TimeBaseMarshaller;
import com.threerings.crowd.data.BodyMarshaller;
import com.threerings.crowd.data.LocationMarshaller;
import com.threerings.crowd.chat.data.ChatMarshaller;

import com.threerings.msoy.chat.client.MsoyChatDirector;
import com.threerings.msoy.chat.data.ChatChannel;

import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.data.all.GroupName;
import com.threerings.msoy.data.all.ChannelName;
import com.threerings.msoy.data.MemberInfo;
import com.threerings.msoy.data.MemberMarshaller;
import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.data.MsoyAuthResponseData;
import com.threerings.msoy.data.MsoyBootstrapData;
import com.threerings.msoy.data.MsoyCredentials;
import com.threerings.msoy.data.SceneBookmarkEntry;

import com.threerings.msoy.data.all.FriendEntry;

/**
 * A client shared by both our virtual world and header incarnations.
 */
public /*abstract*/ class BaseClient extends Client
{
    public static const log :Log = Log.getLog(BaseClient);

    public function dispatchEventToGWT (eventName :String, eventArgs :Array) :void
    {
        try {
            if (ExternalInterface.available) {
                ExternalInterface.call("triggerFlashEvent", eventName, eventArgs);
            }
        } catch (err :Error) {
            Log.getLog(this).warning("triggerFlashEvent failed: " + err);
        }
    }

    public function BaseClient (stage :Stage)
    {
        super(createStartupCreds(stage), stage);
        setVersion(DeploymentConfig.version);

        _ctx = createContext();
        LoggingTargets.configureLogging(_ctx);

        // wire up our JavaScript bridge functions
        try {
            if (ExternalInterface.available) {
                configureExternalFunctions();
            }
        } catch (err :Error) {
            // nada: ExternalInterface isn't there. Oh well!
            log.info("Unable to configure external functions.");
        }

        // allow connecting to the game server
        Security.loadPolicyFile("http://" + DeploymentConfig.serverHost + "/crossdomain.xml");
        // and the media server if it differs from the game server
        if (DeploymentConfig.mediaURL.indexOf(DeploymentConfig.serverHost) == -1) {
            Security.loadPolicyFile(DeploymentConfig.mediaURL + "crossdomain.xml");
        }

        // configure our server and port info and logon
        setServer(DeploymentConfig.serverHost, DeploymentConfig.serverPorts);
    }

    public function fuckingCompiler () :void
    {
        var i :int = TimeBaseMarshaller.GET_TIME_OID;
        i = LocationMarshaller.LEAVE_PLACE;
        i = BodyMarshaller.SET_IDLE;
        i = ChatMarshaller.AWAY;

        var c :Class;
        c = MsoyBootstrapData;
        c = MemberObject;
        c = MemberInfo;
        c = MsoyAuthResponseData;
        c = MemberMarshaller;
        c = SceneBookmarkEntry;

        [ResourceBundle("global")]
        var rb :ResourceBundle;
    }

    // from Client
    override public function gotBootstrap (data :BootstrapData, omgr :DObjectManager) :void
    {
        super.gotBootstrap(data, omgr);

        // save any machineIdent or sessionToken from the server.
        var rdata :MsoyAuthResponseData = (getAuthResponseData() as MsoyAuthResponseData);
        if (rdata.ident != null) {
            Prefs.setMachineIdent(rdata.ident);
        }
        if (rdata.sessionToken != null) {
            Prefs.setSessionToken(rdata.sessionToken);
        }

        if (rdata.sessionToken != null) {
            try {
                if (ExternalInterface.available) {
                    ExternalInterface.call("flashDidLogon", "Foo", 1, rdata.sessionToken);
                }
            } catch (err :Error) {
                log.warning("Unable to inform javascript about login: " + err);
            }
        }

        log.info("Client logged on [built=" + DeploymentConfig.buildTime +
                 ", mediaURL=" + DeploymentConfig.mediaURL +
                 ", staticMediaURL=" + DeploymentConfig.staticMediaURL + "].");
    }

    // from Client
    override public function gotClientObject (clobj :ClientObject) :void
    {
        super.gotClientObject(clobj);

        // set up our logging targets
        LoggingTargets.configureLogging(_ctx);

        // listen for flow and gold updates
        _user = (clobj as MemberObject);
        var updater :LevelUpdater = new LevelUpdater(this);
        _user.addListener(updater);

        // configure our levels to start
        updater.newLevel(_user.level);
        // updater.newGold(_user.gold);
        updater.newFlow(_user.flow);
        updater.newMail(_user.hasNewMail);
    }

    /**
     * Configure any external functions that we wish to expose to JavaScript.
     */
    protected function configureExternalFunctions () :void
    {
        ExternalInterface.addCallback("onUnload", externalOnUnload);
        ExternalInterface.addCallback("getFriends", externalGetFriends);
        ExternalInterface.addCallback("openChannel", externalOpenChannel);
    }

    /**
     * Exposed to JavaScript so that it may notify us when we're leaving the page.
     */
    protected function externalOnUnload () :void
    {
        log.info("Client unloaded. Logging off.");
        logoff(false);
    }

    /**
     * Provides this player's friends list to the GWT client.
     */
    protected function externalGetFriends () :Array
    {
        if (_user == null) {
            log.info("externalGetFriends() without MemberObject.");
            return new Array();
        }

        // we have to convert everything to an array of primitives, so we convert to an array of
        // String, Number, Boolean (repeat) (an array of arrays doesn't work; yay!)
        var friends :Array = _user.getSortedEstablishedFriends();
        var fdata :Array = new Array();
        friends.forEach(function (entry :FriendEntry, index :int, array :Array) :void {
            fdata.push(entry.name.toString());
            fdata.push(entry.name.getMemberId());
            fdata.push(entry.online);
        });
        return fdata;
    }

//     protected function externalGetGroups () :Array
//     {
//     }

    /**
     * Exposed to JavaScript so that it may order us to open chat channels.
     */
    protected function externalOpenChannel (type :int, name :String, id :int) :void
    {
        var nameObj :Name;
        if (type == ChatChannel.FRIEND_CHANNEL) {
            nameObj = new MemberName(name, id);
        } else if (type == ChatChannel.GROUP_CHANNEL) {
            nameObj = new GroupName(name, id);
        } else if (type == ChatChannel.PRIVATE_CHANNEL) {
            nameObj = new ChannelName(name, id);
        } else {
            throw new Error("Unknown channel type: " + type);
        }
        (_ctx.getChatDirector() as MsoyChatDirector).openChannel(nameObj);
    }

    /**
     * Creates the context we'll use with this client.
     */
    protected function createContext () :BaseContext
    {
        return new BaseContext(this);
    }

    /**
     * Create the credentials that will be used to log us on
     */
    protected static function createStartupCreds (stage :Stage, token :String = null)
        :MsoyCredentials
    {
        var params :Object = stage.loaderInfo.parameters;
        var creds :MsoyCredentials;
        if ((params["pass"] != null) && (params["user"] != null)) {
            creds = new MsoyCredentials(new Name(String(params["user"])),
                                        MD5.hash(String(params["pass"])));
        } else {
            creds = new MsoyCredentials(null, null);
        }
        creds.ident = Prefs.getMachineIdent();
        if (null == params["guest"]) {
            creds.sessionToken = (token == null) ? params["token"] : token;
        }
        return creds;
    }

    protected var _ctx :BaseContext;
    protected var _user :MemberObject;
}
}

import flash.external.ExternalInterface;

import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;

import com.threerings.msoy.client.BaseClient;
import com.threerings.msoy.data.MemberObject;

class LevelUpdater implements AttributeChangeListener
{
    public function LevelUpdater (client :BaseClient) {
        _client = client;
    }

    public function attributeChanged (event :AttributeChangedEvent) :void {
        if (event.getName() == MemberObject.LEVEL) {
            newLevel(event.getValue() as int, event.getOldValue() as int);
        /*} else if (event.getName() == MemberObject.GOLD) {
            newGold(event.getValue() as int, event.getOldValue() as int); */
        } else if (event.getName() == MemberObject.FLOW) {
            newFlow(event.getValue() as int, event.getOldValue() as int);
        } else if (event.getName() == MemberObject.HAS_NEW_MAIL) {
            // TODO: support indicating how many new mails the user has?
            newMail(event.getValue() as Boolean, event.getOldValue() as Boolean);
        }
    }

    public function newLevel (level :int, oldLevel :int = 0) :void {
        sendNotification([LEVEL_UPDATE_LEVEL, level, oldLevel]);
    }

    public function newFlow (flow :int, oldFlow :int = 0) :void {
        sendNotification([LEVEL_UPDATE_FLOW, flow, oldFlow]);
    }

    public function newGold (gold :int, oldGold :int = 0) :void {
        sendNotification([LEVEL_UPDATE_GOLD, gold, oldGold]);
    }

    public function newMail (mail :Boolean, oldMail :Boolean = false) :void {
        // TODO: support indication how many new mails the user has?
        sendNotification([LEVEL_UPDATE_MAIL, mail ? 1 : 0, oldMail ? 1 : 0]);
    }

    protected function sendNotification (args :Array) :void {
        _client.dispatchEventToGWT(LEVEL_UPDATE_EVENT, args);
    }

    /** Event dispatched to GWT when we've leveled up */
    protected static const LEVEL_UPDATE_EVENT :String = "levelUpdate";
    protected static const LEVEL_UPDATE_LEVEL :int = 1;
    protected static const LEVEL_UPDATE_FLOW :int = 2;
    protected static const LEVEL_UPDATE_GOLD :int = 3;
    protected static const LEVEL_UPDATE_MAIL :int = 4;

    protected var _client :BaseClient;
}
