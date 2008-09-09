//
// $Id$

package client.me;

import java.util.List;

import com.google.gwt.core.client.GWT;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.VerticalPanel;

import com.threerings.msoy.item.data.all.Item;

import com.threerings.msoy.person.gwt.FeedMessage;
import com.threerings.msoy.person.gwt.MeService;
import com.threerings.msoy.person.gwt.MeServiceAsync;
import com.threerings.msoy.person.gwt.MyWhirledData;

import client.ui.MsoyUI;

import client.util.MsoyCallback;
import client.util.ServiceUtil;


import client.item.StuffNaviBar;
import client.person.FeedPanel;
import client.person.PersonMessages;

public class MyWhirled extends VerticalPanel
{
    public MyWhirled ()
    {
        setStyleName("myWhirled");

        _mesvc.getMyWhirled(new MsoyCallback<MyWhirledData>() {
            public void onSuccess (MyWhirledData data) {
                init(data);
            }
        });
    }

    protected void init (MyWhirledData data)
    {
        add(new StuffNaviBar(Item.NOT_A_TYPE));
        add(MsoyUI.createLabel(_msgs.populationDisplay("" + data.whirledPopulation), "Pop"));
        add(new WhatsNextPanel(data));
        String empty =
            data.friendCount > 0 ? _pmsgs.emptyFeed() : _pmsgs.emptyFeedNoFriends();
        FeedPanel feed = new FeedPanel(empty, true, new FeedPanel.FeedLoader() {
            public void loadFeed (int feedDays, AsyncCallback<List<FeedMessage>> callback) {
                _mesvc.loadFeed(feedDays, callback);
            }
        });
        feed.setFeed(data.feed, false);
        add(feed);
    }

    protected static final MeMessages _msgs = (MeMessages)GWT.create(MeMessages.class);
    protected static final PersonMessages _pmsgs = (PersonMessages)GWT.create(PersonMessages.class);
    protected static final MeServiceAsync _mesvc = (MeServiceAsync)
        ServiceUtil.bind(GWT.create(MeService.class), MeService.ENTRY_POINT);
}
