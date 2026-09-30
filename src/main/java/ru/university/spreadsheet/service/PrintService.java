package ru.university.spreadsheet.service;

import java.util.Objects;
import java.util.logging.Logger;

import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.transform.Scale;
import javafx.stage.Window;

/**
 * Печать содержимого окна.
 * <p>
 * Показывает системный диалог выбора принтера, масштабирует переданный узел под
 * ширину печатной области страницы и отправляет задание принтеру.
 */
public class PrintService {

    private static final Logger LOG = Logger.getLogger(PrintService.class.getName());

    /**
     * Печатает узел сцены.
     *
     * @param node узел, который нужно напечатать, например таблица документа
     * @return {@code true}, если задание передано принтеру; {@code false}, если
     *         пользователь отменил диалог печати
     * @throws ServiceException     если доступных принтеров нет или принтер вернул ошибку
     * @throws NullPointerException если узел равен null
     */
    public boolean print(Node node) {
        Objects.requireNonNull(node, "node");
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            throw new ServiceException("Не найдено ни одного доступного принтера");
        }
        Window owner = node.getScene() == null ? null : node.getScene().getWindow();
        if (!job.showPrintDialog(owner)) {
            job.cancelJob();
            LOG.info("Печать отменена пользователем");
            return false;
        }
        Scale scale = fitToPageWidth(job, node);
        node.getTransforms().add(scale);
        try {
            if (!job.printPage(node)) {
                throw new ServiceException("Принтер вернул ошибку при печати документа");
            }
        } finally {
            node.getTransforms().remove(scale);
        }
        boolean finished = job.endJob();
        if (!finished) {
            throw new ServiceException("Не удалось завершить задание печати");
        }
        LOG.info("Задание печати передано принтеру");
        return true;
    }

    private static Scale fitToPageWidth(PrinterJob job, Node node) {
        PageLayout layout = job.getJobSettings().getPageLayout();
        double width = node.getBoundsInParent().getWidth();
        double factor = width > 0 ? Math.min(1.0, layout.getPrintableWidth() / width) : 1.0;
        return new Scale(factor, factor);
    }
}
