package client.controller;

import client.application.ClientSession;
import client.network.TestSocketClient;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerControllerUiLightTest extends JavaFxTestSupport {

    @BeforeEach
    void setUp() throws Exception {
        injectSharedSocket(new TestSocketClient());
    }

    @AfterEach
    void tearDown() {
        ClientSession.clear();
    }

    @Test
    void auctionFormValidationReadsInjectedJavaFxFields() throws Exception {
        runOnFxThread(() -> {
            SellerController controller = new SellerController();
            FormFields form = injectFormFields(controller);

            assertEquals("Vui lòng nhập tên sản phẩm.", invokeValidation(controller));

            form.txtItemName.setText("Phone");
            assertEquals("Vui lòng chọn loại sản phẩm.", invokeValidation(controller));

            form.cbItemType.setValue("Điện tử");
            assertEquals("Vui lòng nhập mô tả sản phẩm.", invokeValidation(controller));

            form.txtDescription.setText("desc");
            form.txtStartPrice.setText("abc");
            assertEquals("Giá khởi điểm phải là số hợp lệ.", invokeValidation(controller));

            form.txtStartPrice.setText("0");
            assertEquals("Giá khởi điểm phải lớn hơn 0.", invokeValidation(controller));

            form.txtStartPrice.setText("100");
            assertEquals("Vui lòng chọn đầy đủ ngày, giờ và phút bắt đầu.", invokeValidation(controller));

            form.dpStartDate.setValue(LocalDate.now().minusDays(1));
            form.cbStartHour.setValue("23");
            form.cbStartMinute.setValue("59");
            assertEquals("Thời gian bắt đầu phải sau hiện tại.", invokeValidation(controller));

            LocalDateTime start = LocalDateTime.now().plusMinutes(5);
            form.dpStartDate.setValue(start.toLocalDate());
            form.cbStartHour.setValue(String.format("%02d", start.getHour()));
            form.cbStartMinute.setValue(String.format("%02d", start.getMinute()));
            assertEquals("Vui lòng chọn đầy đủ ngày, giờ và phút kết thúc.", invokeValidation(controller));

            form.dpEndDate.setValue(LocalDate.now().minusDays(1));
            form.cbEndHour.setValue("23");
            form.cbEndMinute.setValue("59");
            assertEquals("Thời gian kết thúc phải sau thời gian bắt đầu.", invokeValidation(controller));

            form.dpEndDate.setValue(LocalDate.now().plusDays(1));
            form.cbItemType.setValue("Tác phẩm nghệ thuật");
            form.txtArtist.setText(" ");
            assertEquals("Vui lòng nhập tên nghệ sĩ.", invokeValidation(controller));

            form.cbItemType.setValue("Phương tiện");
            form.txtMileage.setText("-1");
            assertEquals("Số km đã đi phải là số nguyên lớn hơn hoặc bằng 0.", invokeValidation(controller));

            form.cbItemType.setValue("Điện tử");
            assertNull(invokeValidation(controller));
            return null;
        });
    }

    @Test
    void configureEndTimeAndPriceFieldsWorkWithoutScene() throws Exception {
        runOnFxThread(() -> {
            SellerController controller = new SellerController();
            TextField txtStartPrice = new TextField();
            DatePicker dpStartDate = new DatePicker();
            ComboBox<String> cbStartHour = new ComboBox<>();
            ComboBox<String> cbStartMinute = new ComboBox<>();
            DatePicker dpEndDate = new DatePicker();
            ComboBox<String> cbEndHour = new ComboBox<>();
            ComboBox<String> cbEndMinute = new ComboBox<>();

            setField(controller, "txtStartPrice", txtStartPrice);
            setField(controller, "dpStartDate", dpStartDate);
            setField(controller, "cbStartHour", cbStartHour);
            setField(controller, "cbStartMinute", cbStartMinute);
            setField(controller, "dpEndDate", dpEndDate);
            setField(controller, "cbEndHour", cbEndHour);
            setField(controller, "cbEndMinute", cbEndMinute);

            invoke(controller, "configurePriceFields", new Class<?>[0]);
            assertNotNull(txtStartPrice.getTextFormatter());

            invoke(controller, "configureEndTimeFields", new Class<?>[0]);
            assertEquals(24, cbStartHour.getItems().size());
            assertEquals(60, cbStartMinute.getItems().size());
            assertEquals(24, cbEndHour.getItems().size());
            assertEquals(60, cbEndMinute.getItems().size());
            assertNotNull(dpStartDate.getValue());
            assertNotNull(cbStartHour.getValue());
            assertNotNull(cbStartMinute.getValue());
            assertEquals("23", cbEndHour.getValue());
            assertEquals("59", cbEndMinute.getValue());

            StringConverter<LocalDate> converter = dpEndDate.getConverter();
            LocalDate date = LocalDate.of(2026, 6, 1);
            assertEquals("01/06/2026", converter.toString(date));
            assertEquals("", converter.toString(null));
            assertEquals(date, converter.fromString("01/06/2026"));
            assertNull(converter.fromString(" "));
            assertThrows(IllegalArgumentException.class, () -> converter.fromString("bad-date"));

            LocalDateTime selected = (LocalDateTime) invoke(controller, "getSelectedEndTime", new Class<?>[0]);
            assertEquals(dpEndDate.getValue().atTime(23, 59), selected);
            return null;
        });
    }

    @Test
    void clearFormAndCreateAuctionProgressUpdateControls() throws Exception {
        Path selectedFile = Files.createTempFile("seller-ui-light-", ".png");
        Files.write(selectedFile, new byte[] { 1, 2, 3 });
        try {
            runOnFxThread(() -> {
                SellerController controller = new SellerController();
                FormFields form = injectFormFields(controller);
                TextField txtArtYear = new TextField("2024");
                TextField txtMaterial = new TextField("Oil");
                TextField txtBrand = new TextField("Brand");
                TextField txtModel = new TextField("Model");
                TextField txtCondition = new TextField("New");
                TextField txtVehicleBrand = new TextField("Vehicle");
                TextField txtVehicleYear = new TextField("2020");
                ImageView imgPreview = new ImageView();
                Label lblImageStatus = new Label("selected");
                Button btnCreateAuction = new Button();
                Button btnUploadImage = new Button();

                setField(controller, "txtArtYear", txtArtYear);
                setField(controller, "txtMaterial", txtMaterial);
                setField(controller, "txtBrand", txtBrand);
                setField(controller, "txtModel", txtModel);
                setField(controller, "txtCondition", txtCondition);
                setField(controller, "txtVehicleBrand", txtVehicleBrand);
                setField(controller, "txtVehicleYear", txtVehicleYear);
                setField(controller, "imgPreview", imgPreview);
                setField(controller, "lblImageStatus", lblImageStatus);
                setField(controller, "btnCreateAuction", btnCreateAuction);
                setField(controller, "btnUploadImage", btnUploadImage);

                form.txtItemName.setText("Phone");
                form.cbItemType.setValue("Điện tử");
                form.txtStartPrice.setText("100");
                form.txtDescription.setText("desc");
                form.txtArtist.setText("artist");
                form.txtMileage.setText("10");

                invoke(controller, "clearAuctionForm", new Class<?>[0]);
                assertEquals("", form.txtItemName.getText());
                assertNull(form.cbItemType.getValue());
                assertNotNull(form.dpStartDate.getValue());
                assertEquals(LocalDate.now().plusDays(1), form.dpEndDate.getValue());
                assertEquals("Chưa chọn ảnh", lblImageStatus.getText());

                setField(controller, "selectedImageFile", selectedFile.toFile());
                invoke(controller, "setCreateAuctionInProgress", new Class<?>[] { boolean.class }, true);
                assertTrue(btnCreateAuction.isDisabled());
                assertTrue(btnUploadImage.isDisabled());
                assertEquals("Đang tạo...", btnCreateAuction.getText());
                assertEquals("Đang upload: " + selectedFile.getFileName(), lblImageStatus.getText());

                invoke(controller, "setCreateAuctionInProgress", new Class<?>[] { boolean.class }, false);
                assertFalse(btnCreateAuction.isDisabled());
                assertFalse(btnUploadImage.isDisabled());
                assertEquals("Tạo Phiên Đấu Giá", btnCreateAuction.getText());
                assertTrue(lblImageStatus.getText().startsWith("Đã chọn: " + selectedFile.getFileName()));
                return null;
            });
        } finally {
            Files.deleteIfExists(selectedFile);
        }
    }

    private FormFields injectFormFields(SellerController controller) throws Exception {
        FormFields fields = new FormFields(
                new TextField(),
                new ComboBox<>(),
                new TextField(),
                new DatePicker(),
                new ComboBox<>(),
                new ComboBox<>(),
                new DatePicker(),
                new ComboBox<>(),
                new ComboBox<>(),
                new TextArea(),
                new TextField(),
                new TextField());
        setField(controller, "txtItemName", fields.txtItemName);
        setField(controller, "cbItemType", fields.cbItemType);
        setField(controller, "txtStartPrice", fields.txtStartPrice);
        setField(controller, "dpStartDate", fields.dpStartDate);
        setField(controller, "cbStartHour", fields.cbStartHour);
        setField(controller, "cbStartMinute", fields.cbStartMinute);
        setField(controller, "dpEndDate", fields.dpEndDate);
        setField(controller, "cbEndHour", fields.cbEndHour);
        setField(controller, "cbEndMinute", fields.cbEndMinute);
        setField(controller, "txtDescription", fields.txtDescription);
        setField(controller, "vboxDynamicFields", new VBox());
        setField(controller, "lblDynamicTitle", new Label());
        setField(controller, "gridArt", new GridPane());
        setField(controller, "gridElectronics", new GridPane());
        setField(controller, "gridVehicle", new GridPane());
        setField(controller, "txtArtist", fields.txtArtist);
        setField(controller, "txtMileage", fields.txtMileage);
        return fields;
    }

    private String invokeValidation(SellerController controller) throws Exception {
        return (String) invoke(controller, "getAuctionFormValidationError", new Class<?>[0]);
    }

    private void injectSharedSocket(TestSocketClient socketClient) throws Exception {
        Field field = ClientSession.class.getDeclaredField("sharedSocket");
        field.setAccessible(true);
        field.set(null, socketClient);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private record FormFields(
            TextField txtItemName,
            ComboBox<String> cbItemType,
            TextField txtStartPrice,
            DatePicker dpStartDate,
            ComboBox<String> cbStartHour,
            ComboBox<String> cbStartMinute,
            DatePicker dpEndDate,
            ComboBox<String> cbEndHour,
            ComboBox<String> cbEndMinute,
            TextArea txtDescription,
            TextField txtArtist,
            TextField txtMileage) {
    }
}
