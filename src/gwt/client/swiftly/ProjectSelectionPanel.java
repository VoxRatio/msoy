//
// $Id$

package client.swiftly;

import java.util.List;
import java.util.Iterator;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.EnterClickAdapter;
import com.threerings.gwt.ui.Hyperlink;
import com.threerings.gwt.ui.InlineLabel;
import com.threerings.gwt.ui.WidgetUtil;

import com.threerings.msoy.web.data.SwiftlyProject;
import com.threerings.msoy.item.web.Item;

/**
 * Displays the client interface for selecting or creating a swiftly project.
 */
public class ProjectSelectionPanel extends VerticalPanel
{
    public ProjectSelectionPanel ()
    {
        super();
        setWidth("100%");
        setStyleName("projectSelectionPanel");

        _errorContainer = new HorizontalPanel();
        add(_errorContainer);

        FlexTable table = new FlexTable();
        table.setWidth("100%");
        add(table);

        // a list of the member's projects
        _membersProjects = new VerticalPanel();
        _membersProjects.setStyleName("membersProjects");
        _membersHeader = new HorizontalPanel();
        VerticalPanel membersContainer = new VerticalPanel();
        membersContainer.add(_membersHeader);
        membersContainer.add(_membersProjects);

        // a drop down to select the project type
        FlexTable createContainer = new FlexTable();
        _projectTypes = new ListBox();
        _projectTypes.setStyleName("projectTypes");
        _projectTypes.addChangeListener(new ChangeListener() {
            public void onChange (Widget sender) {
                updateSelectedProjectType();
            }
        });
        for (int i = 0; i < SwiftlyProject.PROJECT_TYPES.length; i++) {
            byte type = SwiftlyProject.PROJECT_TYPES[i];
            // TODO: unhack when there is a common way to lookup Item names in i18n land
            _projectTypes.addItem(Item.getTypeName(type), String.valueOf(type));
        }
        updateSelectedProjectType();

        // input fields to create a new project
        final TextBox projectName = new TextBox();
        projectName.setMaxLength(50);
        projectName.setVisibleLength(25);
        _remixable = new CheckBox(CSwiftly.msgs.remixable());

        // a click listener for the create project button
        ClickListener doCreate = new ClickListener() {
            public void onClick (Widget sender) {
                createProject(projectName.getText());
            }
        };

        createContainer.setWidget(0, 0, new InlineLabel(CSwiftly.msgs.startProject()));
        createContainer.setWidget(1, 0, new InlineLabel(CSwiftly.msgs.projectName()));
        createContainer.setWidget(1, 1, projectName);
        createContainer.setWidget(2, 0, new InlineLabel(CSwiftly.msgs.selectType()));
        createContainer.setWidget(2, 1, _projectTypes);
        createContainer.setWidget(3, 0, _remixable);
        createContainer.setWidget(3, 1, new Button(CSwiftly.msgs.createProject(), doCreate));

        // a list of all remixable projects
        _remixableProjects = new VerticalPanel();
        _remixableProjects.setStyleName("remixableProjects");
        _remixableHeader = new HorizontalPanel();
        VerticalPanel remixableContainer = new VerticalPanel();
        remixableContainer.add(_remixableHeader);
        remixableContainer.add(_remixableProjects);

        // layout the main table
        table.setWidget(0, 0, membersContainer);
        table.setWidget(0, 1, createContainer);
        table.setWidget(1, 0, remixableContainer);
        table.getFlexCellFormatter().setColSpan(1, 0, 2);

        // populate the data from the backend
        loadRemixableProjects();
        loadMembersProjects();
    }

    protected void createProject (final String projectName)
    {
        CSwiftly.swiftlysvc.createProject(CSwiftly.creds, projectName, _selectedProjectType, 
            _remixable.isChecked(), new AsyncCallback() {
            public void onSuccess (Object result) {
                CSwiftly.log("Project created: " + projectName);
                SwiftlyProject newProject = (SwiftlyProject)result;
                History.newItem("" + newProject.projectId);
            }
            public void onFailure (Throwable caught) {
                CSwiftly.log("createProject(" + projectName + ") failed", caught);
                addError(CSwiftly.serverError(caught));
            }
        });
    }

    // get the list of projects that are remixable
    protected void loadRemixableProjects ()
    {
        CSwiftly.swiftlysvc.getRemixableProjects(CSwiftly.creds, new AsyncCallback() {
            public void onSuccess (Object result) {
                Iterator iter = ((List)result).iterator();
                if (!iter.hasNext()) {
                    _remixableHeader.add(new Label(CSwiftly.msgs.noRemixableProjects()));
                } else {
                    _remixableHeader.add(new Label(CSwiftly.msgs.remixableProjects()));
                    while (iter.hasNext()) {
                        final SwiftlyProject project = (SwiftlyProject)iter.next();
                        Hyperlink projectLink = new Hyperlink(
                            project.projectName, String.valueOf(project.projectId));
                        DOM.setStyleAttribute(projectLink.getElement(), "display", "inline");
                        _remixableProjects.add(projectLink);
                    }
                }
            }
            public void onFailure (Throwable caught) {
                CSwiftly.log("getMembersProjects failed", caught);
                addError(CSwiftly.serverError(caught));
            }
        });
    }

    // get the list of projects this user is a collaborator on
    protected void loadMembersProjects ()
    {
        CSwiftly.swiftlysvc.getMembersProjects(CSwiftly.creds, new AsyncCallback() {
            public void onSuccess (Object result) {
                Iterator iter = ((List)result).iterator();
                if (!iter.hasNext()) {
                    _membersHeader.add(new Label(CSwiftly.msgs.noMembersProjects()));
                } else {
                    _membersHeader.add(new Label(CSwiftly.msgs.membersProjects()));
                    while (iter.hasNext()) {
                        final SwiftlyProject project = (SwiftlyProject)iter.next();
                        Hyperlink projectLink = new Hyperlink(
                            project.projectName, String.valueOf(project.projectId));
                        DOM.setStyleAttribute(projectLink.getElement(), "display", "inline");
                        _membersProjects.add(projectLink);
                    }
                }
            }
            public void onFailure (Throwable caught) {
                CSwiftly.log("getMembersProjects failed", caught);
                addError(CSwiftly.serverError(caught));
            }
        });
    }

    protected void updateSelectedProjectType ()
    {
        int tx = _projectTypes.getSelectedIndex();
        if (tx == -1) {
            return;
        }
        _selectedProjectType = Byte.parseByte(_projectTypes.getValue(tx));
    }

    protected void addError (String error)
    {
        _errorContainer.add(new Label(error));
    }

    protected void clearErrors ()
    {
        _errorContainer.clear();
    }

    protected VerticalPanel _membersProjects;
    protected VerticalPanel _remixableProjects;
    protected ListBox _projectTypes;
    protected CheckBox _remixable;
    protected HorizontalPanel _membersHeader;
    protected HorizontalPanel _remixableHeader;
    protected HorizontalPanel _errorContainer;
    protected byte _selectedProjectType;


}
