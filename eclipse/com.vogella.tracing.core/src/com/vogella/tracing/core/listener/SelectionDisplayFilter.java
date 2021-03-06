package com.vogella.tracing.core.listener;

import java.util.Optional;

import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.e4.ui.model.application.commands.MCommand;
import org.eclipse.e4.ui.model.application.ui.menu.MHandledItem;
import org.eclipse.e4.ui.workbench.renderers.swt.HandledContributionItem;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.SubContributionItem;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.menus.CommandContributionItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

@SuppressWarnings("restriction")
public class SelectionDisplayFilter implements Listener {

	private static final Logger LOG = LoggerFactory.getLogger(SelectionDisplayFilter.class);

	private enum CommandCallOrigin {
		MENU, TOOLBAR
	}

	private enum ContributionItemType {
		CommandContributionItem, HandledContributionItem, ActionContributionItem
	}

	private static class CommandData {
		final String commandId;
		final String commandName;
		final ContributionItemType contributionItemType;

		public CommandData(String commandId, String commandName, ContributionItemType contributionItemType) {
			this.commandId = commandId;
			this.commandName = commandName;
			this.contributionItemType = contributionItemType;
		}
	}

	private MeterRegistry meterRegistry;

	private Counter menuSelectionCounter;
	private Counter toolbarSelectionCounter;

	public SelectionDisplayFilter(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
		menuSelectionCounter = Counter.builder("selection.menu").tags("selection", "menu").register(meterRegistry);
		toolbarSelectionCounter = Counter.builder("selection.toolbar").tags("selection", "toolbar")
				.register(meterRegistry);
	}

	@Override
	public void handleEvent(Event event) {
		Widget widget = event.widget;

		if (widget instanceof MenuItem) {
			MenuItem menuItem = (MenuItem) widget;
			menuSelectionCounter.increment();

			int menuDepth = getMenuDepth(menuItem.getParent(), 0);
			DistributionSummary menuDepthCounter = meterRegistry.summary("selection.menu", "menuText",
					menuItem.getText());
			menuDepthCounter.record(menuDepth);

			LOG.debug(menuItem.getText() + " has a depth of " + menuDepth);

			handleItemData(menuItem.getData(), CommandCallOrigin.MENU);
		} else if (widget instanceof ToolItem) {
			toolbarSelectionCounter.increment();
			handleItemData(widget.getData(), CommandCallOrigin.TOOLBAR);
		}
	}

	private void handleItemData(Object data, CommandCallOrigin origin) {
		Optional<CommandData> commandData = getCommandData(data);

		commandData.ifPresent(cmdData -> {
			Counter counter = meterRegistry.counter("command.calls.contributionitem", "commandId", cmdData.commandId,
					"commandName", cmdData.commandName, "origin", origin.toString(), "contributionItemType",
					cmdData.contributionItemType.toString());
			counter.increment();
		});
	}

	private Optional<CommandData> getCommandData(Object data) {
		if (data instanceof CommandContributionItem) {
			ParameterizedCommand command = ((CommandContributionItem) data).getCommand();
			try {
				CommandData commandData = new CommandData(command.getId(), command.getName(),
						ContributionItemType.CommandContributionItem);
				return Optional.of(commandData);
			} catch (NotDefinedException e) {
				LOG.error(e.getMessage(), e);
			}
		} else if (data instanceof HandledContributionItem) {
			MHandledItem model = ((HandledContributionItem) data).getModel();
			MCommand command = model.getCommand();
			CommandData commandData = new CommandData(command.getElementId(), command.getCommandName(),
					ContributionItemType.HandledContributionItem);
			return Optional.of(commandData);
		} else if (data instanceof ActionContributionItem) {
			IAction action = ((ActionContributionItem) data).getAction();
			CommandData commandData = new CommandData(action.getActionDefinitionId(), action.getText(),
					ContributionItemType.ActionContributionItem);
			return Optional.of(commandData);
		} else if (data instanceof SubContributionItem) {
			return getCommandData(((SubContributionItem) data).getInnerItem());
		}

		return Optional.empty();
	}

	private int getMenuDepth(Menu parentMenu, int i) {
		if (parentMenu.getParentMenu() != null) {
			return getMenuDepth(parentMenu.getParentMenu(), ++i);
		}

		return i;
	}
}
