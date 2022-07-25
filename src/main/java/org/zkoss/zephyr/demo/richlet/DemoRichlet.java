/* DemoRichlet.java

	Purpose:

	Description:

	History:
		Wed Mar 09 15:38:04 CST 2022, Created by katherine

Copyright (C) 2022 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zephyr.demo.richlet;

import static java.util.Arrays.asList;
// +1 You already provide a lot of constants to navigate the tree
import static org.zkoss.zephyr.action.ActionTarget.NEXT_SIBLING;
import static org.zkoss.zephyr.action.ActionTarget.PARENT;
import static org.zkoss.zephyr.action.ActionTarget.SELF;
import static org.zkoss.zephyr.demo.util.Boilerplate.ORDER_TEMPLATE;
import static org.zkoss.zephyr.demo.util.Boilerplate.PRODUCT_LIST_TEMPLATE;
import static org.zkoss.zephyr.demo.util.Boilerplate.PRODUCT_SIZE_TEMPLATE;
import static org.zkoss.zephyr.demo.util.Boilerplate.SHOPPING_BAG_COLUMN_TEMPLATE;
import static org.zkoss.zephyr.demo.util.Boilerplate.summaryTemplate;
import static org.zkoss.zephyr.demo.util.Helper.getTotalLocatorFromQuantity;
import static org.zkoss.zephyr.demo.util.Helper.log;
import static org.zkoss.zephyr.demo.util.Helper.nextUuid;
import static org.zkoss.zephyr.demo.util.Helper.parseItemId;
import static org.zkoss.zephyr.demo.util.Helper.parseOrderId;
import static org.zkoss.zephyr.demo.util.Helper.uuid;

import java.util.List;
import java.util.stream.Collectors;

import org.zkoss.zephyr.action.data.InputData;
import org.zkoss.zephyr.annotation.Action;
import org.zkoss.zephyr.annotation.ActionVariable;
import org.zkoss.zephyr.annotation.RichletMapping;
import org.zkoss.zephyr.demo.db.factory.DaoFactory;
import org.zkoss.zephyr.demo.db.service.OrderService;
import org.zkoss.zephyr.demo.pojo.Item;
import org.zkoss.zephyr.demo.util.Boilerplate;
import org.zkoss.zephyr.demo.util.Helper;
import org.zkoss.zephyr.ui.Locator;
import org.zkoss.zephyr.ui.Self;
import org.zkoss.zephyr.ui.StatelessRichlet;
import org.zkoss.zephyr.ui.UiAgent;
import org.zkoss.zephyr.zpr.IButton;
import org.zkoss.zephyr.zpr.ICombobox;
import org.zkoss.zephyr.zpr.IComboitem;
import org.zkoss.zephyr.zpr.IComponent;
import org.zkoss.zephyr.zpr.IDiv;
import org.zkoss.zephyr.zpr.IFooter;
import org.zkoss.zephyr.zpr.IGrid;
import org.zkoss.zephyr.zpr.IImage;
import org.zkoss.zephyr.zpr.ILabel;
import org.zkoss.zephyr.zpr.IRow;
import org.zkoss.zephyr.zpr.IRows;
import org.zkoss.zephyr.zpr.ISpinner;
import org.zkoss.zephyr.zpr.IStyle;
import org.zkoss.zephyr.zpr.IVlayout;
import org.zkoss.zk.ui.event.Events;

@RichletMapping("/shoppingCart")
public class DemoRichlet implements StatelessRichlet {

	private static final OrderService orderService = DaoFactory.INSTANCE.getService();
	private static final String DEMO_CSS = "/css/shoppingCart.css";

	@RichletMapping("")
	public List<IComponent> index() {
		return asList(
			IStyle.ofSrc(DEMO_CSS),
			IVlayout.of(
				initShoppingBag(),
				ORDER_TEMPLATE
			)
		);
	}

	private IVlayout initShoppingBag() {
		final String orderId = Helper.nextUuid();
		return IVlayout.of(
			ILabel.of("Shopping bag").withSclass("title"),
			// Even though this is just a demo, please use string constants for everything that is related.
			// This makes it much easier to follow the semantics of the code.
			// I'd also argue for an ID type (class) instead of using String.
			IGrid.ofId("shoppingBag")
				.withHflex("1")
				.withEmptyMessage("please add items.")
				.withColumns(SHOPPING_BAG_COLUMN_TEMPLATE)
				.withRows(intShoppingBagItems(orderId)),
			initOrderButtons(orderId))
		.withSclass("shoppingBag");
	}

	private IRows intShoppingBagItems(String orderId) {
		// #1: I really like the fluent API. Can we have this for the old widgets as well?
		// #2: Please use consistent formatting (only one method call per line)
		return IRows.ofId("shoppingBagRows")
			.withChildren(initShoppingBagItem(orderId));
	}

	private IRow initShoppingBagItem(String orderId) {
		String uuid = orderService.insertItem(orderId);
		int initQuantity = 1;
		int initPrice = Item.DEFAULT_PRODUCT.getPrice();
		return IRow.of(
			initProductList(),
			initProductSize(),
			ISpinner.of(initQuantity)
				.withInstant(true)
				.withAction(this::doQuantityChange),
			ILabel.of(String.valueOf(initPrice)),
			ILabel.of(String.valueOf(initPrice)),
			// withId() instead of "of()"? 
			IButton.of("delete")
				.withAction(this::doDelete)
		).withId(uuid(orderId, uuid));
	}

	private IDiv initOrderButtons(String orderId) {
		return IDiv.of(
			IButton.of("add item +")
				.withAction(this::addItem)
				.withSclass("add-items")
				.withId(uuid(orderId, "add")),
			IButton.of("submit order")
				.withAction(this::doSubmit)
				.withSclass("submit")
				.withId(uuid(orderId, "submit")));
	}

	private ICombobox initProductList() {
		String initProductName = Item.DEFAULT_PRODUCT.getName();
		return ICombobox.of(initProductName)
			.withReadonly(true)
			.withAction(this::doItemChange)
			.withChildren(PRODUCT_LIST_TEMPLATE);
	}

	private ICombobox initProductSize() {
		String initProductSize = "S";
		return ICombobox.of(initProductSize)
			.withReadonly(true)
			.withAction(this::doSizeChange)
			.withChildren(PRODUCT_SIZE_TEMPLATE);
	}

	@Action(type = Events.ON_CLICK)
	// field = "id" seems to be kind of the default?
	// Also, how about bunch of constants for all fields, ideally in the class which contains the field to avoid typos?
	// Lastly, why is this public? It's called as lambda, right?
	public void addItem(@ActionVariable(targetId = SELF, field = "id") String uuid) {
		// I never liked the many static methods which ZK uses. Unless you have an UiAgent for tests, this
		// code makes testing impossible. How about supporting UiAgent as parameter which is automatically
		// wired to the correct instance by the event handling code?
		UiAgent.getCurrent().appendChild(
				// Same here: I would prefer to add a parameter which allows to manipulate the UI element "shoppingBagRows"
				// on which I can call appendChild(). That way, it will be more obvious what UI elements an action
				// will touch. Plus tests can verify that the correct changes happen.
				Locator.ofId("shoppingBagRows"),
				initShoppingBagItem(parseOrderId(uuid)));
		log("add item");
	}

	@Action(type = Events.ON_CLICK)
	// Are you using the HTML element ID to store the database ID?
	// I understand this is just a demo but how about data-id?
	public void doDelete(Self self, @ActionVariable(targetId = PARENT, field = "id") String uuid) {
		orderService.delete(parseItemId(uuid));
		UiAgent.getCurrent().remove(self.closest(IRow.class));
		log("delete item");
	}

	@Action(type = Events.ON_CLICK)
	public void doSubmit(@ActionVariable(targetId = SELF, field = "id") String uuid) {
		final String orderId = parseOrderId(uuid);
		orderService.submit(orderId);
		UiAgent.getCurrent()
				// empty the shopping bag rows
				.replaceChildren(Locator.ofId("shoppingBagRows"))
				// render the order table content
				.replaceChildren(Locator.ofId("orderRows"),
					orderService.selectOrder(orderId).stream()
							.map(Boilerplate::orderItemTemplate)
							.collect(Collectors.toList()))
				// update the summary content
				.replaceChildren(Locator.ofId("summary"),
						summaryTemplate(orderService.count(orderId), orderService.sum(orderId)))
				// reset the order buttons with a new orderId
				.replaceWith(Locator.ofId(uuid).closest(IDiv.class),
						initOrderButtons(nextUuid()));
		log("submit order");
	}

	@Action(type = Events.ON_CHANGE)
	public void doItemChange(InputData data, Self self,
			@ActionVariable(targetId = PARENT, field = "id") String uuid,
			// This code is very brittle when someone changes the UI. Imagine someone adds another element between those two.
			// It's very hard to see all the places which you would need to fix. I forsee a lot of grief because of this.
			// I'd prefer a solution where a "ui structure helper" is used in the fluent API
			// to remember the structure using names or IDs.
			// Given two names, the helper would return the relative path between the two.
			// At the very least, it must be possible to write unit tests that complain when these relative paths break.
			// For example, developers must be able to create a constant for every path and verify it against the result of the fluent API.
			@ActionVariable(targetId = NEXT_SIBLING + NEXT_SIBLING) int quantity) {
		String productName = data.getValue();
		int price = Item.PRODUCT_TABLE.get(productName).getPrice();
		orderService.updateProduct(parseItemId(uuid), productName, quantity * price);
		String subTotal = String.valueOf(quantity * price);
		UiAgent.getCurrent()
				.smartUpdate(Helper.getPriceLocator(self), new ILabel.Updater().value(String.valueOf(price)))
				.smartUpdate(Helper.getTotalLocator(self), new ILabel.Updater().value(subTotal));
		log("change item");
	}

	@Action(type = Events.ON_CHANGE)
	public void doQuantityChange(
		// How about this style for indenting? One parameter per line and the ") {" is on a new line.
		Self self,
		InputData data,
		@ActionVariable(targetId = NEXT_SIBLING) Integer price,
		@ActionVariable(targetId = PARENT, field = "id") String uuid
	) {
		Integer quantity = Integer.valueOf(data.getValue());
		orderService.updateQuantity(parseItemId(uuid), quantity, price);
		UiAgent.getCurrent().smartUpdate(
				getTotalLocatorFromQuantity(self),
			new ILabel.Updater().value(String.valueOf((price * quantity))));
		log("change quantity");
	}

	@Action(type = Events.ON_CHANGE)
	public void doSizeChange(InputData data, @ActionVariable(targetId = PARENT, field = "id") String uuid) {
		orderService.updateSize(parseItemId(uuid), data.getValue());
		log("change size");
	}
}
