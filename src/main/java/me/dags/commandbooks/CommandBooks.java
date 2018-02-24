package me.dags.commandbooks;

import me.dags.commandbus.CommandBus;
import me.dags.commandbus.annotation.Command;
import me.dags.commandbus.annotation.Permission;
import me.dags.commandbus.annotation.Src;
import me.dags.commandbus.fmt.Fmt;
import me.dags.commandbus.fmt.PagFormatter;
import me.dags.config.Config;
import me.dags.config.Node;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.MemoryDataContainer;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.item.inventory.InteractItemEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Author <dags@dags.me>
 */
@Plugin(id = "commandbooks", name = "CommandBooks", version = "1.0", description = ".")
public class CommandBooks {

    private final Path configDir;

    @Inject
    public CommandBooks(@ConfigDir(sharedRoot = false) Path dir) {
        this.configDir = dir;
    }

    @Listener
    public void init(GameInitializationEvent event) {
        CommandBus.create(this).register(this).submit();
    }

    @Listener
    public void swing(InteractItemEvent.Primary.MainHand event, @Root Player player) {
        Optional<List<String>> commands = getCommands(player);
        if (!commands.isPresent()) {
            return;
        }

        event.setCancelled(true);

        Fmt.info("Processing commands...").tell(player);
        long delay = 0L;
        for (String command : commands.get()) {
            Task.builder().execute(() -> Sponge.getCommandManager().process(player, command))
                    .delayTicks(delay)
                    .submit(this);
            delay += 2L;
        }
    }

    @Command("cmd save <name>")
    @Permission("commandbook.command.save")
    public void save(@Src Player player, String name) {
        Optional<List<String>> commands = getCommands(player);
        if (!commands.isPresent()) {
            Fmt.error("You are not holding a book").tell(player);
            return;
        }

        Config books = Config.must(configDir.resolve("users").resolve(player.getIdentifier() + ".conf"));
        Node book = books.node(name.toLowerCase());
        book.set(commands.get());
        books.save();
        Fmt.info("Saved CommandBook ").stress(name).tell(player);
    }

    @Command("cmd load <name>")
    @Permission("commandbook.command.load.self")
    public void load(@Src Player player, String name) {
        load(player, player, name);
    }

    @Command("cmd load <user> <name>")
    @Permission("commandbook.command.load.other")
    public void load(@Src Player player, User user, String name) {
        Config books = Config.must(configDir.resolve("users").resolve(user.getIdentifier() + ".conf"));
        Node book = books.node(name.toLowerCase());
        if (book.isEmpty()) {
            Fmt.error("A book by that name does not exist").tell(player);
            return;
        }
        List<String> pages = new LinkedList<>();
        book.iterate(node -> pages.add(node.get("")));
        ItemStack item = buildBook(name, pages);
        player.getInventory().offer(item);
        Fmt.info("Loaded CommandBook ").stress(name).tell(player);
    }

    @Command("cmd list")
    @Permission("commandbook.command.list.self")
    public void list(@Src Player player) {
        list(player, player);
    }

    @Command("cmd list <user>")
    @Permission("commandbook.command.list.other")
    public void list(@Src Player player, User user) {
        Config books = Config.must(configDir.resolve("users").resolve(user.getIdentifier() + ".conf"));
        PagFormatter page = Fmt.copy().list().lines(10).sort(true);
        page.title().stress("%s's CommandBooks", user.getName());
        books.iterate((k, v) -> page.line().subdued(" - ").info(k));
        page.build().sendTo(player);
    }

    @Command("cmd copy")
    @Permission("commandbook.command.copy")
    public void copy(@Src Player player) {
        Optional<ItemStack> item = player.getItemInHand(HandTypes.MAIN_HAND).filter(i -> i.getItem() == ItemTypes.WRITABLE_BOOK);
        if (!item.isPresent()) {
            Fmt.error("You must be holding a book to use this command").tell(player);
            return;
        }

        ItemStack copy = item.get().copy();
        player.getInventory().offer(copy);
        Fmt.info("Copied CommandBook").tell(player);
    }

    private static Optional<List<String>> getCommands(Player player) {
        Optional<List<Text>> pages = player.getItemInHand(HandTypes.MAIN_HAND)
                .filter(stack -> stack.getItem() == ItemTypes.WRITABLE_BOOK)
                .flatMap(stack -> stack.get(Keys.BOOK_PAGES));

        if (!pages.isPresent()) {
            return Optional.empty();
        }

        List<String> commands = new LinkedList<>();
        for (Text page : pages.get()) {
            String command = page.toPlain().replaceAll("[^\\S ]+", " ");
            commands.add(command);
        }

        return Optional.of(commands);
    }

    // sponge can't correctly build a book from a list of Texts :/
    private static ItemStack buildBook(String name, List<String> pages) {
        MemoryDataContainer container = new MemoryDataContainer();
        container.set(DataQuery.of("ItemType"), "writable_book");
        container.set(DataQuery.of("Count"), 1);
        container.set(DataQuery.of("UnsafeDamage"), 0);
        container.set(DataQuery.of("UnsafeData", "pages"), pages);
        return ItemStack.builder().fromContainer(container).keyValue(Keys.DISPLAY_NAME, Fmt.stress(name).toText()).build();
    }
}
