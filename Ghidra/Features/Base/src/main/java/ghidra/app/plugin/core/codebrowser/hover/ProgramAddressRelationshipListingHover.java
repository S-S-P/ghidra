/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.codebrowser.hover;

import static ghidra.util.HTMLUtilities.bold;
import static ghidra.util.HTMLUtilities.italic;

import javax.swing.JComponent;

import docking.widgets.fieldpanel.field.Field;
import docking.widgets.fieldpanel.support.FieldLocation;
import ghidra.GhidraOptions;
import ghidra.app.plugin.core.hover.AbstractConfigurableHover;
import ghidra.framework.options.Options;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.address.*;
import ghidra.program.model.data.Structure;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.util.AddressFieldLocation;
import ghidra.program.util.ProgramLocation;
import ghidra.util.HTMLUtilities;

/**
 * A hover service to show tool tip text for hovering over a program address in the listing.
 * The tool tip text shows relationships to key topological elements of the program relative to
 * the address -- offset from image base, offset from current memory block; if the address is
 * within the bounds of a function, the offset from function entry point; if the address is within
 * the bounds of defined data, the offset from the start of the data. 
 */
public class ProgramAddressRelationshipListingHover extends AbstractConfigurableHover
		implements ListingHoverService {

	private static final String NAME = "Address Display";
	private static final String DESCRIPTION =
		"Shows the relationship between the hovered address and the base of memory " +
			"and the containing memory block. For addresses in functions, the function " +
			"offset is also shown; for addresses within a complex data (structure, " +
			"array, etc.), the offset from the base of that data is shown.";
	private static final int PRIORITY = 20;

	public ProgramAddressRelationshipListingHover(PluginTool tool) {
		super(tool, PRIORITY);
	}

	@Override
	public void initializeOptions() {
		options = tool.getOptions(GhidraOptions.CATEGORY_BROWSER_POPUPS);
		options.registerOption(NAME, true, null, DESCRIPTION);
		setOptions(options, NAME);
		options.addOptionsChangeListener(this);
	}

	@Override
	public void setOptions(Options options, String optionName) {
		if (optionName.equals(NAME)) {
			enabled = options.getBoolean(NAME, true);
		}
	}

	@Override
	public JComponent getHoverComponent(Program program, ProgramLocation programLocation,
			FieldLocation fieldLocation, Field field) {

		if (!enabled || programLocation == null) {
			return null;
		}

		if (!(programLocation instanceof AddressFieldLocation)) {
			return null;
		}

		StringBuilder sb = new StringBuilder("<HTML><table>");

		Address loc = programLocation.getAddress();
		if (isInDefaultSpace(program, loc)) {
			long imagebaseOffset = loc.subtract(program.getImageBase());
			appendTableRow(sb, "Imagebase Offset", null, imagebaseOffset);
		}

		MemoryBlock block = program.getMemory().getBlock(loc);
		long memblockOffset = loc.subtract(block.getStart());
		appendTableRow(sb, "Memory Block Offset", HTMLUtilities.escapeHTML(block.getName()),
			memblockOffset);

		addFunctionInfo(program, loc, sb);
		addDataInfo(program, loc, sb);

		return createTooltipComponent(sb.toString());
	}

	private boolean isInDefaultSpace(Program p, Address a) {
		AddressFactory factory = p.getAddressFactory();
		AddressSpace defaultSpace = factory.getDefaultAddressSpace();
		return a.getAddressSpace().equals(defaultSpace);
	}

	private void addDataInfo(Program program, Address loc, StringBuilder sb) {

		Data data = program.getListing().getDataContaining(loc);
		if (data == null) {
			return;
		}

		long dataOffset = loc.subtract(data.getAddress());
		if (dataOffset == 0) {
			// no offset to display
			return;
		}

		String dataDescr = "Data Offset";
		if (data.getDataType() instanceof Structure) {
			dataDescr = "Structure Offset";
		}

		String name = data.getLabel(); // prefer the label
		if (name == null) {
			name = data.getDataType().getName();
		}

		if (name == null) {
			// don't think we can get here
			name = italic("Unnamed");
		}

		appendTableRow(sb, dataDescr, name, dataOffset);
	}

	private void addFunctionInfo(Program program, Address loc, StringBuilder sb) {
		Function function = program.getFunctionManager().getFunctionContaining(loc);
		if (function != null) {
			long functionOffset = loc.subtract(function.getEntryPoint());
			appendTableRow(sb, "Function Offset", HTMLUtilities.escapeHTML(function.getName()),
				functionOffset);
		}
	}

	private static void appendTableRow(StringBuilder sb, String title, String reference,
			long offset) {
		sb.append("<tr><td>").append(bold(title)).append("</td>");
		sb.append("<td style=\"text-align: right;\">");
		if (reference != null) {
			sb.append(italic(reference)).append("&nbsp;");
		}
		sb.append(formatOffset(offset));
		sb.append("</td></tr>");
	}

	private static String formatOffset(long offset) {
		return String.format("+%xh", offset);
	}

}
