package it.auties.whatsapp.test;

import it.auties.whatsapp.api.DisconnectReason;
import it.auties.whatsapp.api.QrHandler;
import it.auties.whatsapp.api.WebHistoryLength;
import it.auties.whatsapp.api.Whatsapp;
import it.auties.whatsapp.controller.Keys;
import it.auties.whatsapp.controller.Store;
import it.auties.whatsapp.github.GithubActions;
import it.auties.whatsapp.listener.Listener;
import it.auties.whatsapp.model.button.base.Button;
import it.auties.whatsapp.model.button.base.ButtonText;
import it.auties.whatsapp.model.button.misc.ButtonRow;
import it.auties.whatsapp.model.button.misc.ButtonSection;
import it.auties.whatsapp.model.button.template.hydrated.*;
import it.auties.whatsapp.model.chat.Chat;
import it.auties.whatsapp.model.contact.Contact;
import it.auties.whatsapp.model.contact.ContactJid;
import it.auties.whatsapp.model.info.MessageInfo;
import it.auties.whatsapp.model.interactive.InteractiveButton;
import it.auties.whatsapp.model.interactive.InteractiveHeader;
import it.auties.whatsapp.model.interactive.InteractiveNativeFlow;
import it.auties.whatsapp.model.message.button.*;
import it.auties.whatsapp.model.message.model.MessageContainer;
import it.auties.whatsapp.model.message.model.MessageKey;
import it.auties.whatsapp.model.message.standard.TextMessage;
import it.auties.whatsapp.model.exchange.Node;
import it.auties.whatsapp.util.Json;
import it.auties.whatsapp.utils.ConfigUtils;
import it.auties.whatsapp.util.Smile;
import lombok.SneakyThrows;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.examples.ByteArrayHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

// A mirror of RunCITest for buttons
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public class ButtonsTest implements Listener {
    private static Whatsapp api;
    private static CompletableFuture<Void> future;
    private static CountDownLatch latch;
    private static ContactJid contact;
    private static boolean skip;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @BeforeAll
    public void init() throws IOException, InterruptedException {
        createLatch();
        createApi();
        if (skip) {
            return;
        }
        loadConfig();
        future = api.connectAwaitingLogout();
        latch.await();
    }

    private void createApi() {
        log("Initializing api to start testing...");
        if (!GithubActions.isActionsEnvironment()) {
            if (GithubActions.isReleaseEnv()) {
                System.out.println("Skipping api test: detected local release environment");
                skip = true;
                return;
            }
            api = Whatsapp.webBuilder()
                    .lastConnection()
                    .historyLength(WebHistoryLength.ZERO)
                    .unregistered(QrHandler.toTerminal())
                    .addListener(this)
                    .connect()
                    .join();
            return;
        }
        log("Detected github actions environment");
        api = Whatsapp.customBuilder()
                .store(loadGithubParameter(GithubActions.STORE_NAME, Store.class))
                .keys(loadGithubParameter(GithubActions.CREDENTIALS_NAME, Keys.class))
                .build()
                .addListener(this);
    }

    @SneakyThrows
    private <T> T loadGithubParameter(String parameter, Class<T> type) {
        var passphrase = System.getenv(GithubActions.GPG_PASSWORD);
        var path = Path.of("ci/%s.gpg".formatted(parameter));
        var decrypted = ByteArrayHandler.decrypt(Files.readAllBytes(path), passphrase.toCharArray());
        return Smile.readValue(decrypted, type);
    }

    private void loadConfig() throws IOException {
        if (GithubActions.isActionsEnvironment()) {
            log("Loading environment variables...");
            contact = ContactJid.of(System.getenv(GithubActions.CONTACT_NAME));
            log("Loaded environment variables...");
            return;
        }
        log("Loading configuration file...");
        var props = ConfigUtils.loadConfiguration();
        contact = ContactJid.of(Objects.requireNonNull(props.getProperty("contact"), "Missing contact property in config"));
        log("Loaded configuration file");
    }

    private void createLatch() {
        latch = new CountDownLatch(3);
    }

    private void log(String message, Object... params) {
        System.out.printf(message + "%n", redactParameters(params));
    }

    private Object[] redactParameters(Object... params) {
        if (!GithubActions.isActionsEnvironment()) {
            return params;
        }
        return Arrays.stream(params).map(entry -> "***").toArray(String[]::new);
    }

    @Test
    @Order(1)
    public void testButtonsMessage() {
        if (skip) {
            return;
        }
        log("Sending buttons...");
        var imageButtons = ButtonsMessage.simpleBuilder()
                .header(TextMessage.of("Header"))
                .body("A nice body")
                .footer("A nice footer")
                .buttons(createButtons())
                .build();
        api.sendMessage(contact, imageButtons).join();
        log("Sent buttons");
    }

    @Test
    @Order(1)
    public void testButtonReplyMessage() {
        if (skip) {
            return;
        }
        log("Sending button reply...");
        var imageButtons = Json.readValue("""
                {
                   "buttonsResponseMessage":{
                      "contextInfo":{
                         "quotedMessageId":"0E7F901C06D16F2A",
                         "quotedMessageSenderJid":"393495089819@s.whatsapp.net",
                         "quotedMessageSender":{
                            "jid":"393495089819@s.whatsapp.net",
                            "chosenName":"Alessandro Autiero",
                            "lastKnownPresence":"AVAILABLE",
                            "lastSeen":1681323820.337021700
                         },
                         "quotedMessage":{
                            "buttonsMessage":{
                               "headerText":"Header",
                               "body":"A nice body",
                               "footer":"A nice footer",
                               "buttons":[
                                  {
                                     "id":"089c872c1759",
                                     "text":{
                                        "content":"Button 0"
                                     },
                                     "type":1
                                  },
                                  {
                                     "id":"9043a40b60da",
                                     "text":{
                                        "content":"Button 1"
                                     },
                                     "type":1
                                  },
                                  {
                                     "id":"d2a5a445a2de",
                                     "text":{
                                        "content":"Button 2"
                                     },
                                     "type":1
                                  }
                               ],
                               "headerType":2
                            }
                         }
                      },
                      "buttonId":"d2a5a445a2de",
                      "buttonText":"Button 2",
                      "responseType":1
                   }
                }
                """, MessageContainer.class);
        api.sendMessage(contact, imageButtons).join();
        log("Sent button reply");
    }

    private List<Button> createButtons() {
        return IntStream.range(0, 3)
                .mapToObj(index -> ButtonText.of("Button %s".formatted(index)))
                .map(Button::of)
                .toList();
    }

    @Test
    @Order(2)
    public void testListMessage() {
        if (skip) {
            return;
        }
        var buttons = List.of(ButtonRow.of("First option", "A nice description"), ButtonRow.of("Second option", "A nice description"), ButtonRow.of("Third option", "A nice description"));
        var section = ButtonSection.of("First section", buttons);
        var otherButtons = List.of(ButtonRow.of("First option", "A nice description"), ButtonRow.of("Second option", "A nice description"), ButtonRow.of("Third option", "A nice description"));
        var anotherSection = ButtonSection.of("First section", otherButtons);
        var listMessage = ListMessage.builder()
                .sections(List.of(section, anotherSection))
                .button("Click me")
                .title("A nice title")
                .description("A nice description")
                .footer("A nice footer")
                .listType(ListMessage.Type.SINGLE_SELECT)
                .build();
        var container = MessageContainer.builder()
                .listMessage(listMessage)
                .textMessage(TextMessage.of("Test"))
                .build();
        var keyInfo = MessageKey.builder()
                .chatJid(contact)
                .senderJid(api.store().jid())
                .fromMe(true)
                .build();
        var messageInfo = MessageInfo.builder()
                .key(keyInfo)
                .senderJid(api.store().jid())
                .message(container)
                .build();
        var result = api.sendMessage(messageInfo).join();
        log("Sent list message: " + result);
    }

    @Test
    @Order(3)
    public void testTemplateMessage() {
        if (skip) {
            return;
        }
        log("Sending template message...");
        var quickReplyButton = HydratedTemplateButton.of(HydratedQuickReplyButton.of("Click me"));
        var urlButton = HydratedTemplateButton.of(HydratedURLButton.of("Search it", "https://google.com"));
        var callButton = HydratedTemplateButton.of(HydratedCallButton.of("Call me", contact.toPhoneNumber()));
        var fourRowTemplate = HydratedFourRowTemplate.simpleBuilder()
                .body("A nice body")
                .footer("A nice footer")
                .buttons(List.of(quickReplyButton, urlButton, callButton))
                .build();
        api.sendMessage(contact, TemplateMessage.of(fourRowTemplate)).join();
        log("Sent template message");
    }

    // Just have a test to see if it gets sent, it's not actually a functioning button because it's designed for more complex use cases
    @Test
    @Order(4)
    public void testInteractiveMessage() {
        if (skip) {
            return;
        }
        log("Sending interactive messages..");
        var nativeFlowMessage = InteractiveNativeFlow.builder()
                .buttons(List.of(InteractiveButton.of("review_and_pay"), InteractiveButton.of("review_order")))
                .build();
        var nativeHeader = InteractiveHeader.simpleBuilder()
                .title("Title")
                .subtitle("Subtitle")
                .build();
        var interactiveMessageWithFlow = InteractiveMessage.simpleBuilder()
                .header(nativeHeader)
                .content(nativeFlowMessage)
                .footer("Footer")
                .build();
        api.sendMessage(contact, interactiveMessageWithFlow).join();
        log("Sent interactive messages");
    }

    @SuppressWarnings("JUnit3StyleTestMethodInJUnit4Class")
    @AfterAll
    public void testDisconnect() {
        if (skip) {
            return;
        }
        log("Logging off...");
        CompletableFuture.delayedExecutor(5, TimeUnit.MINUTES).execute(api::disconnect);
        future.join();
        log("Logged off");
    }

    @Override
    public void onNodeSent(Node outgoing) {
        System.out.printf("Sent node %s%n", outgoing);
    }

    @Override
    public void onNodeReceived(Node incoming) {
        System.out.printf("Received node %s%n", incoming);
    }

    @Override
    public void onLoggedIn() {
        latch.countDown();
        log("Logged in: -%s", latch.getCount());
    }

    @Override
    public void onDisconnected(DisconnectReason reason) {
        System.out.printf("Disconnected: %s%n", reason);
    }

    @Override
    public void onContacts(Collection<Contact> contacts) {
        latch.countDown();
        log("Got contacts: -%s", latch.getCount());
    }

    @Override
    public void onChats(Collection<Chat> chats) {
        latch.countDown();
        log("Got chats: -%s", latch.getCount());
    }

    @Override
    public void onChatMessagesSync(Chat contact, boolean last) {
        if (!last) {
            return;
        }
        System.out.printf("%s is ready with %s messages%n", contact.name(), contact.messages().size());
    }

    @Override
    public void onNewMessage(Whatsapp whatsapp, MessageInfo info) {
        System.out.println(info.toJson());
    }
}
