//
// $Id$

package client.groups;

import java.util.List;

import com.google.gwt.core.client.GWT;

import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.util.SimpleDataModel;

import com.threerings.gwt.ui.PagedGrid;
import com.threerings.gwt.ui.PagedWidget;

import com.threerings.msoy.data.all.MediaDesc;
import com.threerings.msoy.data.all.VizMemberName;
import com.threerings.msoy.group.data.all.Medal;
import com.threerings.msoy.group.gwt.GroupService;
import com.threerings.msoy.group.gwt.GroupServiceAsync;
import com.threerings.msoy.group.gwt.GroupService.MedalOwners;
import com.threerings.msoy.group.gwt.GroupService.MedalsResult;
import com.threerings.msoy.web.gwt.Pages;

import client.shell.CShell;
import client.ui.MsoyUI;
import client.ui.RoundBox;
import client.util.Link;
import client.util.MediaUtil;
import client.util.MsoyCallback;
import client.util.ServiceUtil;

public class MedalListPanel extends FlowPanel
{
    public MedalListPanel (int groupId)
    {
        setStyleName("medalListPanel");
        _groupId = groupId;
        _groupsvc.getAwardedMedals(groupId, new MsoyCallback<MedalsResult>() {
            public void onSuccess (MedalsResult result) {
                CShell.frame.setTitle(result.groupName.toString());
                displayMedals(result.medals);
            }
        });
    }

    protected void displayMedals (List<MedalOwners> medals)
    {
        AbsolutePanel header = MsoyUI.createAbsolutePanel("Header");
        header.add(new Image("/images/group/medal_title.png"), 30, 0);
        header.add(MsoyUI.createLabel(_msgs.medalListIntro(), "Intro"), 235, 10);
        header.add(new Image("/images/group/medal_tofu.png"), 466, 0);
        add(header);

        RoundBox medalList = new RoundBox(RoundBox.MEDIUM_BLUE);
        add(medalList);
        medalList.addStyleName("MedalList");
        HorizontalPanel title = new HorizontalPanel();
        title.setStyleName("MedalTitle");
        title.add(new Label(_msgs.medalListHeader()));
        if (CShell.isSupport()) {
            title.add(MsoyUI.createActionLabel(
                _msgs.medalListAddMedal(), "AddMedal", Link.createListener(
                    Pages.GROUPS, GroupsPage.Nav.CREATEMEDAL.composeArgs(_groupId))));
        }
        medalList.add(title);

        PagedGrid<MedalOwners> grid =
            new PagedGrid<MedalOwners>(MEDALS_ROWS, MEDALS_COLS, PagedWidget.NAV_ON_BOTTOM) {
                @Override protected Widget createWidget (MedalOwners owners) {
                    return new MedalOwnerWidget(owners);
                }

                @Override protected String getEmptyMessage () {
                    return _msgs.medalListEmptyList();
                }
            };
        grid.setModel(new SimpleDataModel<MedalOwners>(medals), 0);
        medalList.add(grid);
    }

    protected static class MedalOwnerWidget extends HorizontalPanel
    {
        public MedalOwnerWidget (MedalOwners owners)
        {
            setStyleName("MedalOwnerWidget");
            add(new MedalWidget(owners.medal));

            FlowPanel ownerWidgets = new FlowPanel();
            ownerWidgets.setStyleName("OwnerWidgets");
            for (VizMemberName owner : owners.owners) {
                FlowPanel ownerWidget = new FlowPanel();
                ownerWidget.setStyleName("OwnerWidget");
                ownerWidget.add(MediaUtil.createMediaView(
                    owner.getPhoto(), MediaDesc.THUMBNAIL_SIZE));
                ownerWidget.add(Link.create(
                    owner.toString(), Pages.PEOPLE, "" + owner.getMemberId()));
                ownerWidgets.add(ownerWidget);
            }
            add(ownerWidgets);
        }
    }

    protected static class MedalWidget extends FlowPanel
    {
        public MedalWidget (Medal medal)
        {
            setStyleName("MedalWidget");
            Widget icon = MediaUtil.createMediaView(medal.icon, MediaDesc.THUMBNAIL_SIZE);
            icon.addStyleName("Icon");
            add(icon);
            add(MsoyUI.createLabel(medal.name, "Name"));
            add(MsoyUI.createLabel(medal.description, "Description"));
            // TODO: add support+/manager link for editing medal.
        }
    }

    protected int _groupId;

    protected static final int MEDALS_COLS = 1;
    protected static final int MEDALS_ROWS = 6;

    protected static final GroupsMessages _msgs = GWT.create(GroupsMessages.class);
    protected static final GroupServiceAsync _groupsvc = (GroupServiceAsync)ServiceUtil.bind(
        GWT.create(GroupService.class), GroupService.ENTRY_POINT);
}
