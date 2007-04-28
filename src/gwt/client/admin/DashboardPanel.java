//
// $Id$

package client.admin;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.WidgetUtil;

import com.threerings.msoy.web.client.DeploymentConfig;
import com.threerings.msoy.web.data.ConnectConfig;

import client.shell.Page;
import client.shell.WorldClient;
import client.util.MsoyUI;

/**
 * Displays the various services available to support and admin personnel.
 */
public class DashboardPanel extends FlexTable
{
    public DashboardPanel ()
    {
        setStyleName("dashboardPanel");
        setCellSpacing(0);
        setCellPadding(0);

        int row = 0;

        // add various controls
        HorizontalPanel controls = new HorizontalPanel();
        controls.setSpacing(10);
        setWidget(row++, 0, controls);

        controls.add(new Label(CAdmin.msgs.controls()));
        if (CAdmin.isAdmin()) {
            controls.add(new Button(CAdmin.msgs.displayDashboard(), new ClickListener() {
                public void onClick (Widget sender) {
                    ((Button)sender).setEnabled(false);
                    displayDashboard();
                }
            }));
        }
        controls.add(new Button(CAdmin.msgs.reviewButton(), new ClickListener() {
            public void onClick (Widget sender) {
                new ReviewPopup().show();
            }
        }));
        if (CAdmin.creds.isSupport) {
            controls.add(new Button(CAdmin.msgs.invitePlayers(), new ClickListener() {
                public void onClick (Widget sender) {
                    new InvitePlayersPopup().show();
                }
            }));
        }
        if (CAdmin.isAdmin()) {
            controls.add(new Button(CAdmin.msgs.issueInvites(), new ClickListener() {
                public void onClick (Widget sender) {
                    new IssueInvitesDialog().show();
                }
            }));
        }
    }

    protected void displayDashboard ()
    {
        // load up the information needed to display the dashboard applet
        CAdmin.adminsvc.loadConnectConfig(CAdmin.creds, new AsyncCallback() {
            public void onSuccess (Object result) {
                finishDisplayDashboard((ConnectConfig)result);
            }
            public void onFailure (Throwable cause) {
                setText(getRowCount(), 0, CAdmin.serverError(cause));
            }
        });
    }

    protected void finishDisplayDashboard (ConnectConfig config)
    {
        Page.displayingJava = true;
        WorldClient.clearClient(true);

        int row = getRowCount();
        getFlexCellFormatter().setStyleName(row, 0, "Applet");
        setWidget(row, 0, WidgetUtil.createApplet(
                      "admin", "/clients/" + DeploymentConfig.version + "/admin-client.jar",
                      "com.threerings.msoy.admin.client.AdminApplet", 800, 400,
                      new String[] { "server", config.server,
                                     "port", "" + config.port,
                                     "authtoken", CAdmin.creds.token }));
    }
}
