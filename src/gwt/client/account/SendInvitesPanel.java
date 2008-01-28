//
// $Id$

package client.account;

import java.util.ArrayList;
import java.util.Iterator;

import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.WidgetUtil;
import com.threerings.msoy.web.data.MemberInvites;
import com.threerings.msoy.web.data.InvitationResults;
import com.threerings.msoy.web.data.Invitation;

import client.shell.Frame;
import client.util.BorderedPopup;
import client.util.FlashClients;
import client.util.MsoyCallback;
import client.util.MsoyUI;

/**
 * Display a UI allowing users to send out the invites that have been granted to them, as well
 * as view pending invites they've sent in the past.
 */
public class SendInvitesPanel extends VerticalPanel
{
    /** Originally formulated by lambert@nas.nasa.gov. */
    public static final String EMAIL_REGEX = "^([-A-Za-z0-9_.!%+]+@" +
        "[-a-zA-Z0-9]+(\\.[-a-zA-Z0-9]+)*\\.[-a-zA-Z0-9]+)$";

    public SendInvitesPanel ()
    {
        setSpacing(10);
        setStyleName("sendInvites");
        Frame.setTitle(CAccount.msgs.sendInvitesTitle(), CAccount.msgs.sendInvitesSubtitle());
        reinit();
    }

    protected void reinit ()
    {
        CAccount.membersvc.getInvitationsStatus(CAccount.ident, new MsoyCallback() {
            public void onSuccess (Object result) {
                init((MemberInvites)result);
            }
        });
    }

    protected void init (final MemberInvites invites)
    {
        clear();
        _invites = invites;

        String header = CAccount.msgs.sendInvitesSendHeader("" + invites.availableInvitations);
        add(MsoyUI.createLabel(header, "Header"));

        if (_invites.availableInvitations > 0) {
            add(makeRow(CAccount.msgs.sendInvitesAddresses(),
                        _emailAddresses = MsoyUI.createTextArea("", 40, 4)));

            String tip = CAccount.msgs.sendInvitesSendTip("" + _invites.availableInvitations);
            add(MsoyUI.createLabel(tip, "tipLabel"));

            add(makeRow(CAccount.msgs.sendInvitesFrom(), _fromName = MsoyUI.createTextBox(
                            CAccount.creds.name.toString(), MAX_FROM_LEN, MAX_FROM_LEN)));

            add(_customMessage = MsoyUI.createTextArea(
                    CAccount.msgs.sendInvitesCustomDefault(), 80, 6));

            setHorizontalAlignment(ALIGN_RIGHT);
            _anonymous = new CheckBox(CAccount.msgs.sendInvitesAnonymous());
            Button send = new Button(CAccount.msgs.sendInvitesSendEmail(), new ClickListener() {
                public void onClick (Widget widget) {
                    if ("".equals(_emailAddresses.getText())) {
                        MsoyUI.info(CAccount.msgs.sendInvitesEnterAddresses());
                    } else {
                        checkAndSend();
                    }
                }
            });
            if (CAccount.isAdmin()) {
                add(makeRow(_anonymous, send));
            } else {
                add(send);
            }

        } else {
            add(MsoyUI.createLabel(CAccount.msgs.sendInvitesNoneAvailable(), "tipLabel"));
        }

        setHorizontalAlignment(ALIGN_LEFT);
        add(MsoyUI.createLabel(CAccount.msgs.sendInvitesPendingHeader(), "Header"));

        if (_invites.pendingInvitations.isEmpty()) {
            add(new Label(CAccount.msgs.sendInvitesNoPending()));

        } else {
            add(new Label(CAccount.msgs.sendInvitesPendingTip()));

            int prow = 0;
            FlexTable penders = new FlexTable();
            penders.setStyleName("tipLabel");
            penders.setWidth("100%");
            for (Iterator iter = _invites.pendingInvitations.iterator(); iter.hasNext(); ) {
                Invitation inv = (Invitation)iter.next();
                penders.setText(prow, 0, inv.inviteeEmail);
                penders.setText(prow++, 1, invites.serverUrl + inv.inviteId);
            }
            add(penders);
        }
    }

    protected HorizontalPanel makeRow (String label, Widget right)
    {
        return makeRow(MsoyUI.createLabel(label, "rightLabel"), right);
    }

    protected HorizontalPanel makeRow (Widget label, Widget right)
    {
        HorizontalPanel row = new HorizontalPanel();
        row.add(label);
        row.add(WidgetUtil.makeShim(10, 10));
        row.add(right);
        return row;
    }

    protected void checkAndSend ()
    {
        final ArrayList valid = new ArrayList();
        String[] addresses = _emailAddresses.getText().split("\n");
        for (int ii = 0; ii < addresses.length; ii++) {
            addresses[ii] = addresses[ii].trim();
            if (addresses[ii].matches(EMAIL_REGEX)) {
                if (valid.contains(addresses[ii])) {
                    MsoyUI.info(CAccount.msgs.sendInvitesDuplicateAddress(addresses[ii]));
                    break;
                }
                valid.add(addresses[ii]);
            } else {
                MsoyUI.info(CAccount.msgs.sendInvitesInvalidAddress(addresses[ii]));
                break;
            }
        }
        if (valid.size() != addresses.length) {
            return;
        }

        if (!CAccount.isAdmin() && (valid.size() > _invites.availableInvitations)) {
            MsoyUI.error(CAccount.msgs.sendInvitesTooMany(
                             "" + valid.size(), "" + _invites.availableInvitations));
            return;
        }

        String from = _fromName.getText().trim(), msg = _customMessage.getText().trim();
        boolean anon = _anonymous.isChecked();
        CAccount.membersvc.sendInvites(CAccount.ident, valid, from, msg, anon, new MsoyCallback() {
            public void onSuccess (Object result) {
                FlashClients.tutorialEvent("friendInvited");
                reinit();
                new ResultsPopup(valid, (InvitationResults)result).show();
            }
        });
    }

    protected class ResultsPopup extends BorderedPopup
    {
        public ResultsPopup (ArrayList addrs, InvitationResults invRes)
        {
            VerticalPanel top = new VerticalPanel();
            top.setStyleName("sendInvitesResultsPopup");
            top.setHorizontalAlignment(VerticalPanel.ALIGN_CENTER);
            top.add(MsoyUI.createLabel(CAccount.msgs.sendInvitesResults(), "ResultsHeader"));

            FlexTable contents = new FlexTable();
            contents.setCellSpacing(10);
            top.add(contents);

            int row = 0;
            for (int ii = 0; ii < invRes.results.length; ii++) {
                String addr = (String)addrs.get(ii);
                if (invRes.results[ii] == InvitationResults.SUCCESS) { // null == null
                    contents.setText(row++, 0, CAccount.msgs.sendInvitesResultsSuccessful(addr));
                } else if (invRes.results[ii].startsWith("e.")) {
                    contents.setText(row++, 0, CAccount.msgs.sendInvitesResultsFailed(
                                         addr, CAccount.serverError(invRes.results[ii])));
                } else {
                    contents.setText(row++, 0, CAccount.msgs.sendInvitesResultsFailed(
                                         addr, invRes.results[ii]));
                }
            }

            contents.getFlexCellFormatter().setHorizontalAlignment(
                row, 0, VerticalPanel.ALIGN_RIGHT);
            contents.setWidget(row++, 0, new Button(CAccount.cmsgs.dismiss(), new ClickListener() {
                public void onClick (Widget widget) {
                    hide();
                }
            }));

            setWidget(top);
        }
    }

    protected TextArea _emailAddresses;
    protected TextBox _fromName;
    protected TextArea _customMessage;
    protected CheckBox _anonymous;
    protected MemberInvites _invites;

    protected static final int MAX_FROM_LEN = 40;
}
