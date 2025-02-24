/*
 * This file is part of GriefDefender, licensed under the MIT License (MIT).
 *
 * Copyright (c) bloodmc
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.griefdefender.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.griefdefender.GDPlayerData;
import com.griefdefender.GriefDefenderPlugin;
import com.griefdefender.api.Tristate;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.claim.ClaimResult;
import com.griefdefender.api.claim.ClaimType;
import com.griefdefender.api.claim.ClaimTypes;
import com.griefdefender.api.claim.TrustTypes;
import com.griefdefender.api.permission.Context;
import com.griefdefender.api.permission.option.Options;
import com.griefdefender.cache.MessageCache;
import com.griefdefender.cache.PermissionHolderCache;
import com.griefdefender.claim.GDClaim;
import com.griefdefender.claim.GDClaimManager;
import com.griefdefender.configuration.MessageStorage;
import com.griefdefender.internal.pagination.PaginationList;
import com.griefdefender.internal.util.VecHelper;
import com.griefdefender.permission.GDPermissionHolder;
import com.griefdefender.permission.GDPermissionManager;
import com.griefdefender.permission.GDPermissionUser;
import com.griefdefender.permission.GDPermissions;
import com.griefdefender.text.action.GDCallbackHolder;
import com.griefdefender.util.PermissionUtil;
import com.griefdefender.util.PlayerUtil;

import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.adapter.bukkit.TextAdapter;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@CommandAlias("%griefdefender")
@CommandPermission(GDPermissions.COMMAND_CLAIM_INFO_BASE)
public class CommandClaimInfo extends BaseCommand {

    private final Component NONE = TextComponent.of("none", TextColor.GRAY);
    private final int ADMIN_SETTINGS = 0;
    private final int CLAIM_EXPIRATION = 1;
    private final int DENY_MESSAGES = 2;
    private final int FLAG_OVERRIDES = 3;
    private final int INHERIT_PARENT = 4;
    private final int PVP_OVERRIDE = 5;
    private final int RAID_OVERRIDE = 6;
    private final int RESIZABLE = 7;
    private final int REQUIRES_CLAIM_BLOCKS = 8;
    private final int SIZE_RESTRICTIONS = 9;
    private final int FOR_SALE = 10;
    private boolean useTownInfo = false;

    public CommandClaimInfo() {
        
    }

    public CommandClaimInfo(boolean useTownInfo) {
        this.useTownInfo = useTownInfo;
    }

    @CommandAlias("claiminfo")
    @Syntax("[claim_uuid]")
    @Subcommand("claim info")
    public void execute(CommandSender src, String[] args) {
        String claimIdentifier = null;
        if (args.length > 0) {
            claimIdentifier = args[0];
        }

        Player player = null;
        if (src instanceof Player) {
            player = (Player) src;
            if (!GriefDefenderPlugin.getInstance().claimsEnabledForWorld(player.getWorld().getUID())) {
                GriefDefenderPlugin.sendMessage(src, MessageCache.getInstance().CLAIM_DISABLED_WORLD);
                return;
            }
        }

        if (player == null && claimIdentifier == null) {
            TextAdapter.sendComponent(src, MessageCache.getInstance().COMMAND_CLAIMINFO_NOT_FOUND);
            return;
        }

        boolean isAdmin = src.hasPermission(GDPermissions.COMMAND_ADMIN_CLAIMS);
        final GDPlayerData playerData = player != null ? GriefDefenderPlugin.getInstance().dataStore.getOrCreatePlayerData(player.getWorld(), player.getUniqueId()) : null;
        Claim claim = null;
        if (claimIdentifier == null) {
            if (player != null) {
                claim = GriefDefenderPlugin.getInstance().dataStore.getClaimAt(player.getLocation());
            } else {
                TextAdapter.sendComponent(src, MessageCache.getInstance().COMMAND_CLAIMINFO_UUID_REQUIRED);
                return;
            }
        } else {
            for (World world : Bukkit.getServer().getWorlds()) {
                if (!GriefDefenderPlugin.getInstance().claimsEnabledForWorld(world.getUID())) {
                    continue;
                }

                final GDClaimManager claimManager = GriefDefenderPlugin.getInstance().dataStore.getClaimWorldManager(world.getUID());
                UUID uuid = null;
                try {
                    uuid = UUID.fromString(claimIdentifier);
                    claim = claimManager.getClaimByUUID(uuid).orElse(null);
                    if (claim != null) {
                        break;
                    }
                } catch (IllegalArgumentException e) {
                    
                }
                if (uuid == null) {
                    final List<Claim> claimList = claimManager.getClaimsByName(claimIdentifier);
                    if (!claimList.isEmpty()) {
                        claim = claimList.get(0);
                    }
                }
            }
        }

        if (claim == null) {
            GriefDefenderPlugin.sendMessage(src, MessageCache.getInstance().CLAIM_NOT_FOUND);
            return;
        }

        if (this.useTownInfo) {
            if (!claim.isInTown()) {
                GriefDefenderPlugin.sendMessage(player, MessageCache.getInstance().TOWN_NOT_IN);
                return;
            }
            claim = claim.getTown().get();
        }

        final GDClaim gdClaim = (GDClaim) claim;
        final GDPermissionUser owner = PermissionHolderCache.getInstance().getOrCreateUser(claim.getOwnerUniqueId());
        final UUID ownerUniqueId = claim.getOwnerUniqueId();

        if (!isAdmin) {
            isAdmin = playerData.canIgnoreClaim(gdClaim);
        }
        // if not owner of claim, validate perms
        if (!isAdmin && !player.getUniqueId().equals(claim.getOwnerUniqueId())) {
            if (!gdClaim.getInternalClaimData().getContainers().contains(player.getUniqueId()) 
                    && !gdClaim.getInternalClaimData().getBuilders().contains(player.getUniqueId())
                    && !gdClaim.getInternalClaimData().getManagers().contains(player.getUniqueId())
                    && !player.hasPermission(GDPermissions.COMMAND_CLAIM_INFO_OTHERS)) {
                TextAdapter.sendComponent(player, MessageCache.getInstance().CLAIM_NOT_YOURS);
                return;
            }
        }

        final Component allowEdit = gdClaim.allowEdit(player);

        List<Component> textList = new ArrayList<>();
        Component name = claim.getName().orElse(null);
        Component greeting = claim.getData().getGreeting().orElse(null);
        Component farewell = claim.getData().getFarewell().orElse(null);
        String accessors = "";
        String builders = "";
        String containers = "";
        String managers = "";
        String accessorGroups = "";
        String builderGroups = "";
        String containerGroups = "";
        String managerGroups = "";

        final int minClaimLevel = gdClaim.getOwnerMinClaimLevel();
        double claimY = gdClaim.getOwnerPlayerData() == null ? 65.0D : (minClaimLevel > 65.0D ? minClaimLevel : 65);
        if (gdClaim.isCuboid()) {
            claimY = gdClaim.lesserBoundaryCorner.getY();
        }

        Location southWest = new Location(gdClaim.getWorld(), gdClaim.lesserBoundaryCorner.getX(), claimY, gdClaim.greaterBoundaryCorner.getZ());
        Location northWest = new Location(gdClaim.getWorld(), gdClaim.lesserBoundaryCorner.getX(), claimY, gdClaim.lesserBoundaryCorner.getZ());
        Location southEast = new Location(gdClaim.getWorld(), gdClaim.greaterBoundaryCorner.getX(), claimY, gdClaim.greaterBoundaryCorner.getZ());
        Location northEast = new Location(gdClaim.getWorld(), gdClaim.greaterBoundaryCorner.getX(), claimY, gdClaim.lesserBoundaryCorner.getZ());
        // String southWestCorner = 
        Date created = null;
        Date lastActive = null;
        try {
            Instant instant = claim.getData().getDateCreated();
            created = Date.from(instant);
        } catch(DateTimeParseException ex) {
            // ignore
        }

        try {
            Instant instant = claim.getData().getDateLastActive();
            lastActive = Date.from(instant);
        } catch(DateTimeParseException ex) {
            // ignore
        }

        final int sizeX = Math.abs(claim.getGreaterBoundaryCorner().getX() - claim.getLesserBoundaryCorner().getX()) + 1;
        final int sizeY = Math.abs(claim.getGreaterBoundaryCorner().getY() - claim.getLesserBoundaryCorner().getY()) + 1;
        final int sizeZ = Math.abs(claim.getGreaterBoundaryCorner().getZ() - claim.getLesserBoundaryCorner().getZ()) + 1;
        Component claimSize = TextComponent.empty();
        if (claim.isCuboid()) {
            claimSize = TextComponent.builder(" ")
                    .append(MessageCache.getInstance().LABEL_AREA.color(TextColor.YELLOW))
                    .append(": ", TextColor.YELLOW)
                    .append(sizeX + "x" + sizeY + "x" + sizeZ, TextColor.GRAY).build();
        } else {
            claimSize = TextComponent.builder(" ")
                    .append(MessageCache.getInstance().LABEL_AREA.color(TextColor.YELLOW))
                    .append(": ", TextColor.YELLOW)
                    .append(sizeX + "x" + sizeZ, TextColor.GRAY).build();
        }
        final Component claimCost = TextComponent.builder("  ")
                .append(MessageCache.getInstance().LABEL_BLOCKS.color(TextColor.YELLOW))
                .append(": ", TextColor.YELLOW)
                .append(String.valueOf(claim.getClaimBlocks()), TextColor.GRAY).build();
        if (claim.isWilderness() && name == null) {
            name = TextComponent.of("Wilderness", TextColor.GREEN);
        }
        Component claimName = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_NAME.color(TextColor.YELLOW))
                .append(" : ", TextColor.YELLOW)
                .append(name == null ? NONE : name).build();
        Component worldName = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_WORLD.color(TextColor.YELLOW))
                .append(" : ")
                .append(gdClaim.getWorld().getName(), TextColor.GRAY).build();
        if (!claim.isWilderness() && !claim.isAdminClaim()) {
            claimName = TextComponent.builder()
                    .append(claimName)
                    .append("  ")
                    .append(worldName)
                    .append(claimSize)
                    .append(claimCost).build();
        }
        // users
        final List<UUID> accessorList = gdClaim.getUserTrustList(TrustTypes.ACCESSOR, true);
        final List<UUID> builderList = gdClaim.getUserTrustList(TrustTypes.BUILDER, true);
        final List<UUID> containerList = gdClaim.getUserTrustList(TrustTypes.CONTAINER, true);
        final List<UUID> managerList = gdClaim.getUserTrustList(TrustTypes.MANAGER, true);
        for (UUID uuid : accessorList) {
            final GDPermissionUser user = PermissionHolderCache.getInstance().getOrCreateUser(uuid);
            final String userName = user.getFriendlyName();
            if (userName != null) {
                accessors += userName + " ";
            }
        }
        for (UUID uuid : builderList) {
            final GDPermissionUser user = PermissionHolderCache.getInstance().getOrCreateUser(uuid);
            final String userName = user.getFriendlyName();
            if (userName != null) {
                builders += userName + " ";
            }
        }
        for (UUID uuid : containerList) {
            final GDPermissionUser user = PermissionHolderCache.getInstance().getOrCreateUser(uuid);
            final String userName = user.getFriendlyName();
            if (userName != null) {
                containers += userName + " ";
            }
        }
        for (UUID uuid : managerList) {
            final GDPermissionUser user = PermissionHolderCache.getInstance().getOrCreateUser(uuid);
            final String userName = user.getFriendlyName();
            if (userName != null) {
                managers += userName + " ";
            }
        }

        // groups
        for (String group : gdClaim.getGroupTrustList(TrustTypes.ACCESSOR, true)) {
            accessorGroups += group + " ";
        }
        for (String group : gdClaim.getGroupTrustList(TrustTypes.BUILDER, true)) {
            builderGroups += group + " ";
        }
        for (String group : gdClaim.getGroupTrustList(TrustTypes.CONTAINER, true)) {
            containerGroups += group + " ";
        }
        for (String group : gdClaim.getGroupTrustList(TrustTypes.MANAGER, true)) {
            managerGroups += group + " ";
        }

        /*if (gpClaim.isInTown()) {
            Text returnToClaimInfo = Text.builder().append(Text.of(
                    TextColors.WHITE, "\n[", TextColors.AQUA, "Return to standard settings", TextColors.WHITE, "]\n"))
                .onClick(TextActions.executeCallback(CommandHelper.createCommandConsumer(src, "claiminfo", ""))).build();
            Text townName = Text.of(TextColors.YELLOW, "Name", TextColors.WHITE, " : ", TextColors.RESET,
                    gpClaim.getTownClaim().getTownData().getName().orElse(NONE));
            Text townTag = Text.of(TextColors.YELLOW, "Tag", TextColors.WHITE, " : ", TextColors.RESET,
                    gpClaim.getTownClaim().getTownData().getTownTag().orElse(NONE));
            townTextList.add(returnToClaimInfo);
            townTextList.add(townName);
            townTextList.add(townTag);
            Text townSettings = Text.builder()
                    .append(Text.of(TextStyles.ITALIC, TextColors.GREEN, TOWN_SETTINGS))
                    .onClick(TextActions.executeCallback(createSettingsConsumer(src, claim, townTextList, ClaimTypes.TOWN)))
                    .onHover(TextActions.showText(Text.of("Click here to view town settings")))
                    .build();
            textList.add(townSettings);
        }*/

        if (isAdmin) {
            Component adminSettings = TextComponent.builder()
                    .append(MessageCache.getInstance().CLAIMINFO_UI_ADMIN_SETTINGS).decoration(TextDecoration.ITALIC, true).color(TextColor.RED)
                    .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(createSettingsConsumer(src, claim, generateAdminSettings(src, gdClaim), ClaimTypes.ADMIN))))
                    .hoverEvent(HoverEvent.showText(MessageCache.getInstance().CLAIMINFO_UI_CLICK_ADMIN))
                    .build();
            textList.add(adminSettings);
        }

        Component bankInfo = null;
        Component forSaleText = null;
        if (GriefDefenderPlugin.getInstance().getVaultProvider() != null) {
             if (GriefDefenderPlugin.getActiveConfig(gdClaim.getWorld().getUID()).getConfig().claim.bankTaxSystem) {
                 bankInfo = TextComponent.builder()
                         .append(MessageCache.getInstance().CLAIMINFO_UI_BANK_INFO.color(TextColor.GOLD).decoration(TextDecoration.ITALIC, true))
                         .hoverEvent(HoverEvent.showText(MessageCache.getInstance().CLAIMINFO_UI_BANK_INFO))
                         .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(Consumer -> { CommandHelper.displayClaimBankInfo(src, gdClaim, gdClaim.isTown() ? true : false, true); })))
                         .build();
             }
             forSaleText = TextComponent.builder()
                     .append(MessageCache.getInstance().CLAIMINFO_UI_FOR_SALE.color(TextColor.YELLOW))
                     .append(" : ")
                     .append(getClickableInfoText(src, claim, FOR_SALE, claim.getEconomyData().isForSale() ? MessageCache.getInstance().LABEL_YES.color(TextColor.GREEN) : MessageCache.getInstance().LABEL_NO.color(TextColor.GRAY))).build();
             if (claim.getEconomyData().isForSale()) {
                 forSaleText = TextComponent.builder()
                         .append(forSaleText)
                         .append("  ")
                         .append(MessageCache.getInstance().LABEL_PRICE.color(TextColor.YELLOW))
                         .append(" : ")
                         .append(String.valueOf(claim.getEconomyData().getSalePrice()), TextColor.GOLD)
                         .build();
             }
        }

        Component claimId = TextComponent.builder()
                .append("UUID", TextColor.YELLOW)
                .append(" : ")
                .append(TextComponent.builder()
                        .append(claim.getUniqueId().toString(), TextColor.GRAY)
                        .insertion(claim.getUniqueId().toString()).build()).build();
        final String ownerName = PlayerUtil.getInstance().getUserName(ownerUniqueId);
        Component ownerLine = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_OWNER.color(TextColor.YELLOW))
                .append(" : ")
                .append(ownerName != null && !claim.isAdminClaim() && !claim.isWilderness() ? ownerName : "administrator", TextColor.GOLD).build();
        Component adminShowText = TextComponent.empty();
        Component basicShowText = TextComponent.empty();
        Component subdivisionShowText = TextComponent.empty();
        Component townShowText = TextComponent.empty();
        Component claimType = TextComponent.empty();
        final Component whiteOpenBracket = TextComponent.of("[");
        final Component whiteCloseBracket = TextComponent.of("]");
        Component defaultTypeText = TextComponent.builder()
                .append(whiteOpenBracket)
                .append(gdClaim.getFriendlyNameType(true))
                .append(whiteCloseBracket).build();
        if (allowEdit != null && !isAdmin) {
            adminShowText = allowEdit;
            basicShowText = allowEdit;
            subdivisionShowText = allowEdit;
            townShowText = allowEdit;
            Component adminTypeText = TextComponent.builder()
                    .append(claim.getType() == ClaimTypes.ADMIN ? 
                            defaultTypeText : TextComponent.of("ADMIN", TextColor.GRAY))
                    .hoverEvent(HoverEvent.showText(adminShowText)).build();
            Component basicTypeText = TextComponent.builder()
                    .append(claim.getType() == ClaimTypes.BASIC ? 
                            defaultTypeText : TextComponent.of("BASIC", TextColor.GRAY))
                    .hoverEvent(HoverEvent.showText(basicShowText)).build();
            Component subTypeText = TextComponent.builder()
                    .append(claim.getType() == ClaimTypes.SUBDIVISION ? 
                            defaultTypeText : TextComponent.of("SUBDIVISION", TextColor.GRAY))
                    .hoverEvent(HoverEvent.showText(subdivisionShowText)).build();
            Component townTypeText = TextComponent.builder()
                    .append(claim.getType() == ClaimTypes.TOWN ? 
                            defaultTypeText : TextComponent.of("TOWN", TextColor.GRAY))
                    .hoverEvent(HoverEvent.showText(townShowText)).build();
            claimType = TextComponent.builder()
                    .append(claim.isCuboid() ? "3D " : "2D ", TextColor.GREEN)
                    .append(adminTypeText)
                    .append(" ")
                    .append(basicTypeText)
                    .append(" ")
                    .append(subTypeText)
                    .append(" ")
                    .append(townTypeText)
                    .build();
        } else {
            Component adminTypeText = defaultTypeText;
            Component basicTypeText = defaultTypeText;
            Component subTypeText = defaultTypeText;
            Component townTypeText = defaultTypeText;
            if (!claim.isAdminClaim()) {
                final Component message = ((GDClaim) claim).validateClaimType(ClaimTypes.ADMIN, ownerUniqueId, playerData).getMessage().orElse(null);
                adminShowText = message != null ? message : MessageStorage.MESSAGE_DATA.getMessage(MessageStorage.CLAIMINFO_UI_CLICK_CHANGE_CLAIM,
                        ImmutableMap.of("type", TextComponent.of("ADMIN ", TextColor.RED)));

                if (message == null) {
                    adminTypeText = TextComponent.builder()
                        .append(claim.getType() == ClaimTypes.ADMIN ? 
                                defaultTypeText : TextComponent.of("ADMIN", TextColor.GRAY))
                        .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(createClaimTypeConsumer(src, claim, ClaimTypes.ADMIN, isAdmin))))
                        .hoverEvent(HoverEvent.showText(adminShowText)).build();
                } else {
                    adminTypeText = TextComponent.builder()
                        .append(claim.getType() == ClaimTypes.ADMIN ? 
                                defaultTypeText : TextComponent.of("ADMIN", TextColor.GRAY))
                        .hoverEvent(HoverEvent.showText(adminShowText)).build();
                }
            }
            if (!claim.isBasicClaim()) {
                final Component message = ((GDClaim) claim).validateClaimType(ClaimTypes.BASIC, ownerUniqueId, playerData).getMessage().orElse(null);
                basicShowText = message != null ? message : MessageStorage.MESSAGE_DATA.getMessage(MessageStorage.CLAIMINFO_UI_CLICK_CHANGE_CLAIM,
                        ImmutableMap.of("type", TextComponent.of("BASIC ", TextColor.YELLOW)));

                if (message == null) {
                    basicTypeText = TextComponent.builder()
                            .append(claim.getType() == ClaimTypes.BASIC ? defaultTypeText : TextComponent.of("BASIC", TextColor.GRAY))
                            .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(createClaimTypeConsumer(src, claim, ClaimTypes.BASIC, isAdmin))))
                            .hoverEvent(HoverEvent.showText(basicShowText)).build();
                } else {
                    basicTypeText = TextComponent.builder()
                            .append(claim.getType() == ClaimTypes.BASIC ? defaultTypeText : TextComponent.of("BASIC", TextColor.GRAY))
                            .hoverEvent(HoverEvent.showText(basicShowText)).build();
                }
            }
            if (!claim.isSubdivision()) {
                final Component message = ((GDClaim) claim).validateClaimType(ClaimTypes.SUBDIVISION, ownerUniqueId, playerData).getMessage().orElse(null);
                subdivisionShowText = message != null ? message : MessageStorage.MESSAGE_DATA.getMessage(MessageStorage.CLAIMINFO_UI_CLICK_CHANGE_CLAIM,
                        ImmutableMap.of("type", TextComponent.of("SUBDIVISION ", TextColor.AQUA)));

                if (message == null) {
                    subTypeText = TextComponent.builder()
                            .append(claim.getType() == ClaimTypes.SUBDIVISION ? defaultTypeText : TextComponent.of("SUBDIVISION", TextColor.GRAY))
                            .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(createClaimTypeConsumer(src, claim, ClaimTypes.SUBDIVISION, isAdmin))))
                            .hoverEvent(HoverEvent.showText(subdivisionShowText)).build();
                } else {
                    subTypeText = TextComponent.builder()
                            .append(claim.getType() == ClaimTypes.SUBDIVISION ? defaultTypeText : TextComponent.of("SUBDIVISION", TextColor.GRAY))
                            .hoverEvent(HoverEvent.showText(subdivisionShowText)).build();
                }
            }
            if (!claim.isTown()) {
                final Component message = ((GDClaim) claim).validateClaimType(ClaimTypes.TOWN, ownerUniqueId, playerData).getMessage().orElse(null);
                townShowText = message != null ? message : MessageStorage.MESSAGE_DATA.getMessage(MessageStorage.CLAIMINFO_UI_CLICK_CHANGE_CLAIM,
                        ImmutableMap.of("type", TextComponent.of("TOWN ", TextColor.GREEN)));

                if (message == null) {
                    townTypeText = TextComponent.builder()
                            .append(claim.getType() == ClaimTypes.TOWN ? defaultTypeText : TextComponent.of("TOWN", TextColor.GRAY))
                            .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(createClaimTypeConsumer(src, claim, ClaimTypes.TOWN, isAdmin))))
                            .hoverEvent(HoverEvent.showText(townShowText)).build();
                } else {
                    townTypeText = TextComponent.builder()
                            .append(claim.getType() == ClaimTypes.TOWN ? defaultTypeText : TextComponent.of("TOWN", TextColor.GRAY))
                            .hoverEvent(HoverEvent.showText(townShowText)).build();
                }
            }

            claimType = TextComponent.builder()
                    .append(claim.isCuboid() ? "3D " : "2D ", TextColor.GREEN)
                    .append(adminTypeText)
                    .append(" ")
                    .append(basicTypeText)
                    .append(" ")
                    .append(subTypeText)
                    .append(" ")
                    .append(townTypeText)
                    .build();
        }

        Component claimTypeInfo = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_TYPE.color(TextColor.YELLOW))
                .append(" : ")
                .append(claimType).build();
        Component claimInherit = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_INHERIT.color(TextColor.YELLOW))
                .append(" : ")
                .append(getClickableInfoText(src, claim, INHERIT_PARENT, claim.getData().doesInheritParent() ? TextComponent.of("ON", TextColor.GREEN) : TextComponent.of("OFF", TextColor.RED))).build();
        Component claimExpired = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_EXPIRED.color(TextColor.YELLOW))
                .append(" : ")
                .append(claim.getData().isExpired() ? TextComponent.of("YES", TextColor.RED) : TextComponent.of("NO", TextColor.GRAY)).build();
        Component claimFarewell = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_FAREWELL.color(TextColor.YELLOW))
                .append(" : ")
                .append(farewell == null ? NONE : farewell).build();
        Component claimGreeting = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_GREETING.color(TextColor.YELLOW))
                .append(" : ")
                .append(greeting == null ? NONE : greeting).build();
        Component claimDenyMessages = TextComponent.builder()
                .append(MessageCache.getInstance().CLAIMINFO_UI_DENY_MESSAGES.color(TextColor.YELLOW))
                .append(" : ")
                .append(getClickableInfoText(src, claim, DENY_MESSAGES, claim.getData().allowDenyMessages() ? TextComponent.of("ON", TextColor.GREEN) : TextComponent.of("OFF", TextColor.RED))).build();
        Component claimPvP = TextComponent.builder()
                .append("PvP", TextColor.YELLOW)
                .append(" : ")
                .append(getClickableInfoText(src, claim, PVP_OVERRIDE, claim.getData().getPvpOverride() == Tristate.TRUE ? TextComponent.of("ON", TextColor.GREEN) : TextComponent.of("OFF", TextColor.RED))).build();
        Component claimRaid = TextComponent.builder()
                .append("Raid", TextColor.YELLOW)
                .append(" : ")
                .append(getClickableInfoText(src, claim, RAID_OVERRIDE, GDPermissionManager.getInstance().getInternalOptionValue(TypeToken.of(Boolean.class), owner, Options.RAID, gdClaim) == true ? TextComponent.of("ON", TextColor.GREEN) : TextComponent.of("OFF", TextColor.RED))).build();
        Component claimSpawn = null;
        if (claim.getData().getSpawnPos().isPresent()) {
            Vector3i spawnPos = claim.getData().getSpawnPos().get();
            Location spawnLoc = new Location(gdClaim.getWorld(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
            claimSpawn = TextComponent.builder()
                    .append(MessageCache.getInstance().LABEL_SPAWN.color(TextColor.GREEN))
                    .append(" : ")
                    .append(spawnPos.toString(), TextColor.GRAY)
                    .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(CommandHelper.createTeleportConsumer(player, spawnLoc, claim))))
                    .hoverEvent(HoverEvent.showText(MessageCache.getInstance().CLAIMINFO_UI_TELEPORT_SPAWN))
                    .build();
        }
        Component southWestCorner = TextComponent.builder()
                .append("SW", TextColor.LIGHT_PURPLE)
                .append(" : ")
                .append(VecHelper.toVector3i(southWest).toString(), TextColor.GRAY)
                .append(" ")
                .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(CommandHelper.createTeleportConsumer(player, southWest, claim))))
                .hoverEvent(HoverEvent.showText(MessageStorage.MESSAGE_DATA.getMessage(MessageStorage.CLAIMINFO_UI_TELEPORT_DIRECTION, 
                        ImmutableMap.of("direction", TextComponent.of("SW").color(TextColor.AQUA)))))
                .build();
        Component southEastCorner = TextComponent.builder()
                .append("SE", TextColor.LIGHT_PURPLE)
                .append(" : ")
                .append(VecHelper.toVector3i(southEast).toString(), TextColor.GRAY)
                .append(" ")
                .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(CommandHelper.createTeleportConsumer(player, southEast, claim))))
                .hoverEvent(HoverEvent.showText(MessageStorage.MESSAGE_DATA.getMessage(MessageStorage.CLAIMINFO_UI_TELEPORT_DIRECTION, 
                        ImmutableMap.of("direction", TextComponent.of("SE").color(TextColor.AQUA)))))
                .build();
        Component southCorners = TextComponent.builder()
                .append(MessageCache.getInstance().CLAIMINFO_UI_SOUTH_CORNERS.color(TextColor.YELLOW))
                .append(" : ")
                .append(southWestCorner)
                .append(southEastCorner).build();
        Component northWestCorner = TextComponent.builder()
                .append("NW", TextColor.LIGHT_PURPLE)
                .append(" : ")
                .append(VecHelper.toVector3i(northWest).toString(), TextColor.GRAY)
                .append(" ")
                .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(CommandHelper.createTeleportConsumer(player, northWest, claim))))
                .hoverEvent(HoverEvent.showText(MessageStorage.MESSAGE_DATA.getMessage(MessageStorage.CLAIMINFO_UI_TELEPORT_DIRECTION, 
                        ImmutableMap.of("direction", TextComponent.of("NW").color(TextColor.AQUA)))))
                .build();
        Component northEastCorner = TextComponent.builder()
                .append("NE", TextColor.LIGHT_PURPLE)
                .append(" : ")
                .append(VecHelper.toVector3i(northEast).toString(), TextColor.GRAY)
                .append(" ")
                .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(CommandHelper.createTeleportConsumer(player, northEast, claim))))
                .hoverEvent(HoverEvent.showText(MessageStorage.MESSAGE_DATA.getMessage(MessageStorage.CLAIMINFO_UI_TELEPORT_DIRECTION, 
                        ImmutableMap.of("direction", TextComponent.of("NE").color(TextColor.AQUA)))))
                .build();
        Component northCorners = TextComponent.builder()
                .append(MessageCache.getInstance().CLAIMINFO_UI_NORTH_CORNERS.color(TextColor.YELLOW))
                .append(" : ")
                .append(northWestCorner)
                .append(northEastCorner).build();
        Component claimAccessors = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_ACCESSORS.color(TextColor.YELLOW))
                .append(" : ")
                .append(accessors.equals("") ? NONE : TextComponent.of(accessors, TextColor.BLUE))
                .append(" ")
                .append(accessorGroups, TextColor.LIGHT_PURPLE).build();
        Component claimBuilders = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_BUILDERS.color(TextColor.YELLOW))
                .append(" : ")
                .append(builders.equals("") ? NONE : TextComponent.of(builders, TextColor.BLUE))
                .append(" ")
                .append(builderGroups, TextColor.LIGHT_PURPLE).build();
        Component claimContainers = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_CONTAINERS.color(TextColor.YELLOW))
                .append(" : ")
                .append(containers.equals("") ? NONE : TextComponent.of(containers, TextColor.BLUE))
                .append(" ")
                .append(containerGroups, TextColor.LIGHT_PURPLE).build();
        Component claimCoowners = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_MANAGERS.color(TextColor.YELLOW))
                .append(" : ")
                .append(managers.equals("") ? NONE : TextComponent.of(managers, TextColor.BLUE))
                .append(" ")
                .append(managerGroups, TextColor.LIGHT_PURPLE).build();
        Component dateCreated = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_CREATED.color(TextColor.YELLOW))
                .append(" : ")
                .append(created != null ? TextComponent.of(created.toString(), TextColor.GRAY) : MessageCache.getInstance().LABEL_UNKNOWN.color(TextColor.GRAY)).build();
        Component dateLastActive = TextComponent.builder()
                .append(MessageCache.getInstance().CLAIMINFO_UI_LAST_ACTIVE.color(TextColor.YELLOW))
                .append(" : ")
                .append(lastActive != null ? TextComponent.of(lastActive.toString(), TextColor.GRAY) : MessageCache.getInstance().LABEL_UNKNOWN.color(TextColor.GRAY)).build();

        if (claimSpawn != null) {
            textList.add(claimSpawn);
        }
        if (bankInfo != null) {
            textList.add(bankInfo);
        }
        textList.add(claimName);
        textList.add(ownerLine);
        textList.add(claimTypeInfo);
        if (!claim.isAdminClaim() && !claim.isWilderness()) {
            textList.add(TextComponent.builder()
                    .append(claimInherit)
                    .append("   ")
                    .append(claimExpired).build());
            if (forSaleText != null) {
                textList.add(forSaleText);
            }
        }
        textList.add(TextComponent.builder()
                .append(claimPvP)
                .append("   ")
                .append(claimRaid)
                .append("   ")
                .append(claimDenyMessages)
                .build());
        textList.add(claimAccessors);
        textList.add(claimBuilders);
        textList.add(claimContainers);
        textList.add(claimCoowners);
        textList.add(claimGreeting);
        textList.add(claimFarewell);
        textList.add(dateCreated);
        textList.add(dateLastActive);
        textList.add(claimId);
        textList.add(northCorners);
        textList.add(southCorners);
        if (!claim.getParent().isPresent()) {
            textList.remove(claimInherit);
        }
        if (claim.isAdminClaim()) {
            textList.remove(bankInfo);
            textList.remove(dateLastActive);
        }
        if (claim.isWilderness()) {
            textList.remove(bankInfo);
            textList.remove(claimInherit);
            textList.remove(claimTypeInfo);
            textList.remove(dateLastActive);
            textList.remove(northCorners);
            textList.remove(southCorners);
        }

        PaginationList.Builder paginationBuilder = PaginationList.builder()
                .title(MessageCache.getInstance().CLAIMINFO_UI_TITLE_CLAIMINFO.color(TextColor.AQUA)).padding(TextComponent.of(" ").decoration(TextDecoration.STRIKETHROUGH, true)).contents(textList);
        paginationBuilder.sendTo(src);
    }

    public static Consumer<CommandSender> createSettingsConsumer(CommandSender src, Claim claim, List<Component> textList, ClaimType type) {
        return settings -> {
            Component name = type == ClaimTypes.TOWN ? MessageCache.getInstance().CLAIMINFO_UI_TOWN_SETTINGS : MessageCache.getInstance().CLAIMINFO_UI_ADMIN_SETTINGS;
            PaginationList.Builder paginationBuilder = PaginationList.builder()
                    .title(name.color(TextColor.AQUA)).padding(TextComponent.of(" ").decoration(TextDecoration.STRIKETHROUGH, true)).contents(textList);
            paginationBuilder.sendTo(src);
        };
    }

    private List<Component> generateAdminSettings(CommandSender src, GDClaim claim) {
        List<Component> textList = new ArrayList<>();
        Component returnToClaimInfo = TextComponent.builder()
                .append("\n[")
                .append(MessageCache.getInstance().CLAIMINFO_UI_RETURN_SETTINGS.color(TextColor.AQUA))
                .append("]\n")
            .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(CommandHelper.createCommandConsumer(src, "claiminfo", claim.getUniqueId().toString())))).build();
        Component claimResizable = TextComponent.builder()
                .append(MessageCache.getInstance().LABEL_RESIZABLE.color(TextColor.YELLOW))
                .append(" : ")
                .append(getClickableInfoText(src, claim, RESIZABLE, claim.getInternalClaimData().isResizable() ? TextComponent.of("ON", TextColor.GREEN) : TextComponent.of("OFF", TextColor.RED))).build();
        Component claimRequiresClaimBlocks = TextComponent.builder()
                .append(MessageCache.getInstance().CLAIMINFO_UI_REQUIRES_CLAIM_BLOCKS.color(TextColor.YELLOW))
                .append(" : ")
                .append(getClickableInfoText(src, claim, REQUIRES_CLAIM_BLOCKS, claim.getInternalClaimData().requiresClaimBlocks() ? TextComponent.of("ON", TextColor.GREEN) : TextComponent.of("OFF", TextColor.RED))).build();
        Component claimSizeRestrictions = TextComponent.builder()
                .append(MessageCache.getInstance().CLAIMINFO_UI_SIZE_RESTRICTIONS.color(TextColor.YELLOW))
                .append(" : ")
                .append(getClickableInfoText(src, claim, SIZE_RESTRICTIONS, claim.getInternalClaimData().hasSizeRestrictions() ? TextComponent.of("ON", TextColor.GREEN) : TextComponent.of("OFF", TextColor.RED))).build();
        Component claimExpiration = TextComponent.builder()
                .append(MessageCache.getInstance().CLAIMINFO_UI_CLAIM_EXPIRATION.color(TextColor.YELLOW))
                .append(" : ")
                .append(getClickableInfoText(src, claim, CLAIM_EXPIRATION, claim.getInternalClaimData().allowExpiration() ? TextComponent.of("ON", TextColor.GREEN) : TextComponent.of("OFF", TextColor.RED))).build();
        Component claimFlagOverrides = TextComponent.builder()
                .append(MessageCache.getInstance().CLAIMINFO_UI_FLAG_OVERRIDES.color(TextColor.YELLOW))
                .append(" : ")
                .append(getClickableInfoText(src, claim, FLAG_OVERRIDES, claim.getInternalClaimData().allowFlagOverrides() ? TextComponent.of("ON", TextColor.GREEN) : TextComponent.of("OFF", TextColor.RED))).build();
        textList.add(returnToClaimInfo);
        if (!claim.isAdminClaim() && !claim.isWilderness()) {
            textList.add(claimRequiresClaimBlocks);
            textList.add(claimExpiration);
            textList.add(claimResizable);
            textList.add(claimSizeRestrictions);
        }
        textList.add(claimFlagOverrides);
        int fillSize = 20 - (textList.size() + 4);
        for (int i = 0; i < fillSize; i++) {
            textList.add(TextComponent.of(" "));
        }
        return textList;
    }

    private void executeAdminSettings(CommandSender src, GDClaim claim) {
        PaginationList.Builder paginationBuilder = PaginationList.builder()
                .title(MessageCache.getInstance().CLAIMINFO_UI_ADMIN_SETTINGS).padding(TextComponent.of(" ").decoration(TextDecoration.STRIKETHROUGH, true)).contents(generateAdminSettings(src, claim));
        paginationBuilder.sendTo(src);
    }

    public Component getClickableInfoText(CommandSender src, Claim claim, int titleId, Component infoText) {
        Component onClickText = MessageCache.getInstance().CLAIMINFO_UI_CLICK_TOGGLE;
        boolean hasPermission = true;
        if (src instanceof Player) {
            Component denyReason = ((GDClaim) claim).allowEdit((Player) src);
            if (denyReason != null) {
                onClickText = denyReason;
                hasPermission = false;
            }
        }

        TextComponent.Builder textBuilder = TextComponent.builder()
                .append(infoText)
                .hoverEvent(HoverEvent.showText(onClickText));
        if (hasPermission) {
            textBuilder.clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(createClaimInfoConsumer(src, claim, titleId))));
        }
        return textBuilder.build();
    }

    private Consumer<CommandSender> createClaimInfoConsumer(CommandSender src, Claim claim, int titleId) {
        GDClaim gpClaim = (GDClaim) claim;
        return info -> {
            switch (titleId) {
                case INHERIT_PARENT : 
                    if (!claim.getParent().isPresent() || !src.hasPermission(GDPermissions.COMMAND_CLAIM_INHERIT)) {
                        return;
                    }

                    gpClaim.getInternalClaimData().setInheritParent(!gpClaim.getInternalClaimData().doesInheritParent());
                    gpClaim.getInternalClaimData().setRequiresSave(true);
                    claim.getData().save();
                    CommandHelper.executeCommand(src, "claiminfo", gpClaim.getUniqueId().toString());
                    return;
                case CLAIM_EXPIRATION :
                    gpClaim.getInternalClaimData().setExpiration(!gpClaim.getInternalClaimData().allowExpiration());
                    gpClaim.getInternalClaimData().setRequiresSave(true);
                    gpClaim.getClaimStorage().save();
                    break;
                case DENY_MESSAGES :
                    gpClaim.getInternalClaimData().setDenyMessages(!gpClaim.getInternalClaimData().allowDenyMessages());
                    gpClaim.getInternalClaimData().setRequiresSave(true);
                    gpClaim.getClaimStorage().save();
                    CommandHelper.executeCommand(src, "claiminfo", gpClaim.getUniqueId().toString());
                    return;
                case FLAG_OVERRIDES :
                    gpClaim.getInternalClaimData().setFlagOverrides(!gpClaim.getInternalClaimData().allowFlagOverrides());
                    gpClaim.getInternalClaimData().setRequiresSave(true);
                    gpClaim.getClaimStorage().save();
                    break;
                case PVP_OVERRIDE :
                    Tristate value = gpClaim.getInternalClaimData().getPvpOverride();
                    if (value == Tristate.UNDEFINED) {
                        gpClaim.getInternalClaimData().setPvpOverride(Tristate.TRUE);
                    } else if (value == Tristate.TRUE) {
                        gpClaim.getInternalClaimData().setPvpOverride(Tristate.FALSE);
                    } else {
                        gpClaim.getInternalClaimData().setPvpOverride(Tristate.UNDEFINED);
                    }
                    gpClaim.getInternalClaimData().setRequiresSave(true);
                    gpClaim.getClaimStorage().save();
                    CommandHelper.executeCommand(src, "claiminfo", gpClaim.getUniqueId().toString());
                    return;
                case RAID_OVERRIDE :
                    GDPermissionHolder holder = null;
                    final GDPlayerData playerData = ((GDClaim) claim).getOwnerPlayerData();
                    if (playerData == null) {
                        holder = GriefDefenderPlugin.DEFAULT_HOLDER;
                    } else {
                        holder = playerData.getSubject();
                    }
                    final Boolean result = GDPermissionManager.getInstance().getInternalOptionValue(TypeToken.of(Boolean.class), holder, Options.RAID, gpClaim);
                    final Set<Context> contexts = new HashSet<>();
                    contexts.add(claim.getContext());
                    if (result) {
                        PermissionUtil.getInstance().setOptionValue(holder, Options.RAID.getPermission(), "false", contexts);
                    } else {
                        PermissionUtil.getInstance().setOptionValue(holder, Options.RAID.getPermission(), "true", contexts);
                    }
                    CommandHelper.executeCommand(src, "claiminfo", gpClaim.getUniqueId().toString());
                    return;
                case RESIZABLE :
                    boolean resizable = gpClaim.getInternalClaimData().isResizable();
                    gpClaim.getInternalClaimData().setResizable(!resizable);
                    gpClaim.getInternalClaimData().setRequiresSave(true);
                    gpClaim.getClaimStorage().save();
                    break;
                case REQUIRES_CLAIM_BLOCKS :
                    boolean requiresClaimBlocks = gpClaim.getInternalClaimData().requiresClaimBlocks();
                    gpClaim.getInternalClaimData().setRequiresClaimBlocks(!requiresClaimBlocks);
                    gpClaim.getInternalClaimData().setRequiresSave(true);
                    gpClaim.getClaimStorage().save();
                    break;
                case SIZE_RESTRICTIONS :
                    boolean sizeRestrictions = gpClaim.getInternalClaimData().hasSizeRestrictions();
                    gpClaim.getInternalClaimData().setSizeRestrictions(!sizeRestrictions);
                    gpClaim.getInternalClaimData().setRequiresSave(true);
                    gpClaim.getClaimStorage().save();
                    break;
                case FOR_SALE :
                    boolean forSale = gpClaim.getEconomyData().isForSale();
                    gpClaim.getEconomyData().setForSale(!forSale);
                    gpClaim.getInternalClaimData().setRequiresSave(true);
                    gpClaim.getClaimStorage().save();
                    CommandHelper.executeCommand(src, "claiminfo", gpClaim.getUniqueId().toString());
                    return;
                default:
            }
            executeAdminSettings(src, gpClaim);
        };
    }

    private static Consumer<CommandSender> createClaimTypeConsumer(CommandSender src, Claim gpClaim, ClaimType clicked, boolean isAdmin) {
        GDClaim claim = (GDClaim) gpClaim;
        return type -> {
            if (!(src instanceof Player)) {
                // ignore
                return;
            }

            final Player player = (Player) src;
            if (!isAdmin && ((GDClaim) gpClaim).allowEdit(player) != null) {
                TextAdapter.sendComponent(src, MessageCache.getInstance().CLAIM_NOT_YOURS);
                return;
            }
            final ClaimResult result = claim.changeType(clicked, Optional.of(player.getUniqueId()), src);
            if (result.successful()) {
                CommandHelper.executeCommand(src, "claiminfo", gpClaim.getUniqueId().toString());
            } else {
                TextAdapter.sendComponent(src, result.getMessage().get());
            }
        };
    }
}
