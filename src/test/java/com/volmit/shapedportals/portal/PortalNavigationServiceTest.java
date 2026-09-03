package com.volmit.shapedportals.portal;

import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import com.volmit.shapedportals.geometry.PortalAxis;
import com.volmit.shapedportals.localization.ShapedMessages;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortalNavigationServiceTest {
    @Test
    void sortsPortalsDeterministicallyByWorldAndAnchor() {
        PortalRecord later = portal("00000000-0000-0000-0000-000000000003", "world_b", 2, 64, 0, PortalAxis.X);
        PortalRecord first = portal("00000000-0000-0000-0000-000000000001", "world_a", 1, 64, 0, PortalAxis.X);
        PortalRecord second = portal("00000000-0000-0000-0000-000000000002", "world_a", 2, 64, 0, PortalAxis.X);

        assertThat(PortalNavigationService.sorted(List.of(later, second, first)))
                .containsExactly(first, second, later);
    }

    @Test
    void resolvesFullIdsAndUniquePrefixes() {
        PortalRecord first = portal("aaaaaaaa-1000-0000-0000-000000000001", "world", 0, 64, 0, PortalAxis.X);
        PortalRecord second = portal("aaaaaaaa-2000-0000-0000-000000000002", "world", 2, 64, 0, PortalAxis.X);

        assertThat(PortalNavigationService.resolve(List.of(first, second), first.id().toString()).record())
                .isEqualTo(first);
        assertThat(PortalNavigationService.resolve(List.of(first, second), "aaaaaaaa-1").record())
                .isEqualTo(first);
        assertThat(PortalNavigationService.resolve(List.of(first, second), "#aaaaaaaa-1").record())
                .isEqualTo(first);
    }

    @Test
    void rejectsShortMissingAndAmbiguousPrefixes() {
        PortalRecord first = portal("aaaaaaaa-1000-0000-0000-000000000001", "world", 0, 64, 0, PortalAxis.X);
        PortalRecord second = portal("aaaaaaaa-2000-0000-0000-000000000002", "world", 2, 64, 0, PortalAxis.X);

        assertThat(PortalNavigationService.resolve(List.of(first, second), "aaaa").status())
                .isEqualTo(PortalNavigationService.SelectionStatus.NOT_FOUND);
        assertThat(PortalNavigationService.resolve(List.of(first, second), "bbbbbbbb").status())
                .isEqualTo(PortalNavigationService.SelectionStatus.NOT_FOUND);
        assertThat(PortalNavigationService.resolve(List.of(first, second), "aaaaaaaa").status())
                .isEqualTo(PortalNavigationService.SelectionStatus.AMBIGUOUS);
    }

    @Test
    void buildsLandingCandidatesOnThePortalNormal() {
        PortalRecord xPortal = portal("00000000-0000-0000-0000-000000000001", "world", 4, 64, 8, PortalAxis.X);
        PortalRecord zPortal = portal("00000000-0000-0000-0000-000000000002", "world", 4, 64, 8, PortalAxis.Z);

        assertThat(PortalNavigationService.landingCandidates(xPortal))
                .contains(
                        new BlockPosition(4, 64, 7),
                        new BlockPosition(4, 63, 7),
                        new BlockPosition(4, 64, 9),
                        new BlockPosition(4, 63, 9)
                );
        assertThat(PortalNavigationService.landingCandidates(zPortal))
                .contains(
                        new BlockPosition(3, 64, 8),
                        new BlockPosition(3, 63, 8),
                        new BlockPosition(5, 64, 8),
                        new BlockPosition(5, 63, 8)
                );
    }

    @Test
    void endPortalLandingCandidatesStandAboveTheSolidFrame() {
        BlockPosition anchor = new BlockPosition(4, 64, 8);
        List<BlockPosition> frame = List.of(
                new BlockPosition(3, 64, 8),
                new BlockPosition(5, 64, 8),
                new BlockPosition(4, 64, 7),
                new BlockPosition(4, 64, 9)
        );
        PortalRecord portal = new PortalRecord(
                PortalRecord.CURRENT_SCHEMA_VERSION,
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                UUID.nameUUIDFromBytes("world".getBytes(StandardCharsets.UTF_8)),
                "world",
                PortalAxis.Y,
                anchor,
                List.of(anchor),
                frame,
                List.of(Material.END_PORTAL_FRAME, Material.END_PORTAL_FRAME,
                        Material.END_PORTAL_FRAME, Material.END_PORTAL_FRAME),
                1L,
                "tester"
        );

        assertThat(portal.type()).isEqualTo(PortalType.END);
        assertThat(PortalNavigationService.landingCandidates(portal))
                .containsExactlyInAnyOrder(
                        new BlockPosition(3, 65, 8),
                        new BlockPosition(5, 65, 8),
                        new BlockPosition(4, 65, 7),
                        new BlockPosition(4, 65, 9)
                )
                .doesNotContain(new BlockPosition(4, 65, 8));
    }

    @Test
    void boundsAndDeduplicatesLandingCandidates() {
        PortalRecord portal = portal("00000000-0000-0000-0000-000000000001", "world", 4, 64, 8, PortalAxis.X);

        List<BlockPosition> candidates = PortalNavigationService.landingCandidates(portal);

        assertThat(candidates).doesNotHaveDuplicates();
        assertThat(candidates.size()).isLessThanOrEqualTo(portal.interior().size() * 4);
    }

    @Test
    void groupsBoundaryCandidatesByTheirOwningChunks() {
        PortalRecord portal = portal("00000000-0000-0000-0000-000000000001", "world", 15, 64, 8, PortalAxis.Z);

        assertThat(PortalNavigationService.landingChunks(portal))
                .extracting(PortalNavigationService.LandingChunk::x)
                .containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void generatedTeleportCommandsUseKeyedOptionalParameters() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertThat(PortalNavigationService.teleportCommand(id))
                .isEqualTo("/shapedportals teleport portal=" + id);
    }

    @Test
    void portalHoverPlacesTheTeleportActionOnItsSecondLine() {
        assertThat(ShapedMessages.PORTAL_LIST_HOVER.english())
                .isEqualTo("&7UUID:&r &f{uuid}&r\n&aClick to teleport to this portal.&r");
    }

    @Test
    void portalCardsReserveAWarningLineAndFourEntriesPerDirectorPage() {
        assertThat(ShapedMessages.PORTAL_LIST_ENTRY.english().split("\\n"))
                .hasSize(3);
        List<String> entries = List.of("1", "2", "3", "4", "5");
        DirectorMiniMenu.ContentMenu first = PortalNavigationService.portalMenu(entries, "empty", 1);
        DirectorMiniMenu.ContentMenu second = PortalNavigationService.portalMenu(entries, "empty", 2);

        assertThat(first.page())
                .isEqualTo(new DirectorMiniMenu.ContentPage(1, 2, 0, 4, 5));
        assertThat(second.page())
                .isEqualTo(new DirectorMiniMenu.ContentPage(2, 2, 4, 5, 5));
    }

    @Test
    void reportsOnlySnapshotMaterialsRetiredFromNewPortalCreation() {
        assertThat(PortalNavigationService.retiredFrameMaterials(
                List.of(Material.DIRT, Material.OBSIDIAN, Material.DIRT),
                Set.of(Material.OBSIDIAN, Material.CRYING_OBSIDIAN)))
                .containsExactly(Material.DIRT);
        assertThat(PortalNavigationService.retiredFrameMaterials(
                List.of(Material.DIRT, Material.OBSIDIAN, Material.DIRT),
                Set.of(Material.DIRT, Material.OBSIDIAN, Material.CRYING_OBSIDIAN)))
                .isEmpty();
        assertThat(PortalNavigationService.formatMaterials(List.of(Material.CRYING_OBSIDIAN, Material.DIRT)))
                .isEqualTo("CRYING_OBSIDIAN, DIRT");
    }

    @Test
    void portalCreationTimesStopAtWholeSeconds() {
        assertThat(PortalNavigationService.formatCreatedAt(1_725_000_123_456L))
                .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void unsafeFallbackUsesTheFirstInBoundsLandingCandidate() {
        PortalNavigationService.LandingChunk belowWorld = new PortalNavigationService.LandingChunk(
                0, 0, List.of(new BlockPosition(0, -1, 0)));
        BlockPosition expected = new BlockPosition(1, 64, 1);
        PortalNavigationService.LandingChunk valid = new PortalNavigationService.LandingChunk(
                0, 0, List.of(expected, new BlockPosition(2, 64, 2)));

        assertThat(PortalNavigationService.unsafeCandidate(List.of(belowWorld, valid), 0, 256))
                .isEqualTo(expected);
    }

    private PortalRecord portal(String id, String world, int x, int y, int z, PortalAxis axis) {
        BlockPosition anchor = new BlockPosition(x, y, z);
        return new PortalRecord(
                PortalRecord.CURRENT_SCHEMA_VERSION,
                UUID.fromString(id),
                UUID.nameUUIDFromBytes(world.getBytes(StandardCharsets.UTF_8)),
                world,
                axis,
                anchor,
                List.of(anchor),
                List.of(new BlockPosition(x, y - 1, z)),
                List.of(Material.OBSIDIAN),
                1L,
                "tester"
        );
    }
}
