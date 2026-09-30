package ru.university.spreadsheet.formula;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

import ru.university.spreadsheet.model.CellRef;

/**
 * Граф зависимостей ячеек документа.
 * <p>
 * Ребро связывает зависимую ячейку (содержащую формулу) с влияющей ячейкой, на которую
 * формула ссылается. Граф позволяет обнаруживать циклические ссылки и определять порядок
 * пересчёта зависимых ячеек.
 */
public class DependencyGraph {

    private static final Comparator<CellRef> BY_POSITION =
            Comparator.comparingInt(CellRef::row).thenComparingInt(CellRef::col);

    /** Зависимая ячейка — множество влияющих на неё ячеек. */
    private final Map<CellRef, Set<CellRef>> edges = new HashMap<>();

    /** Влияющая ячейка — множество зависящих от неё ячеек (обратный индекс). */
    private final Map<CellRef, Set<CellRef>> dependents = new HashMap<>();

    /**
     * Добавляет зависимость: значение ячейки {@code dependent} вычисляется по значению
     * ячейки {@code precedent}.
     *
     * @param dependent зависимая ячейка с формулой
     * @param precedent влияющая ячейка, на которую ссылается формула
     * @throws NullPointerException если один из адресов равен null
     */
    public void addDependency(CellRef dependent, CellRef precedent) {
        Objects.requireNonNull(dependent, "dependent");
        Objects.requireNonNull(precedent, "precedent");
        edges.computeIfAbsent(dependent, key -> new LinkedHashSet<>()).add(precedent);
        this.dependents.computeIfAbsent(precedent, key -> new LinkedHashSet<>()).add(dependent);
    }

    /**
     * Удаляет все зависимости ячейки от других ячеек. Вызывается перед перестроением
     * зависимостей при изменении формулы. Зависимости других ячеек от неё сохраняются.
     *
     * @param ref адрес ячейки
     */
    public void removeDependencies(CellRef ref) {
        Set<CellRef> precedents = edges.remove(ref);
        if (precedents == null) {
            return;
        }
        for (CellRef precedent : precedents) {
            Set<CellRef> linked = dependents.get(precedent);
            if (linked != null) {
                linked.remove(ref);
                if (linked.isEmpty()) {
                    dependents.remove(precedent);
                }
            }
        }
    }

    /**
     * Возвращает ячейки, на которые непосредственно ссылается формула указанной ячейки.
     *
     * @param ref адрес ячейки
     * @return неизменяемое множество влияющих ячеек; пустое, если зависимостей нет
     */
    public Set<CellRef> getPrecedents(CellRef ref) {
        return Collections.unmodifiableSet(edges.getOrDefault(ref, Set.of()));
    }

    /**
     * Возвращает ячейки, формулы которых непосредственно ссылаются на указанную ячейку.
     *
     * @param ref адрес ячейки
     * @return неизменяемое множество зависимых ячеек; пустое, если таких нет
     */
    public Set<CellRef> getDependents(CellRef ref) {
        return Collections.unmodifiableSet(dependents.getOrDefault(ref, Set.of()));
    }

    /**
     * Проверяет, участвует ли ячейка в циклической зависимости.
     * <p>
     * Обход в глубину по влияющим ячейкам с цветной маркировкой: белый — вершина не
     * посещалась, серый — находится в текущем пути обхода, чёрный — полностью обработана.
     * Повторный заход в серую вершину означает цикл.
     *
     * @param ref адрес ячейки
     * @return {@code true}, если из ячейки достижим цикл
     */
    public boolean hasCycle(CellRef ref) {
        return visit(Objects.requireNonNull(ref, "ref"), new HashMap<>());
    }

    /**
     * Возвращает порядок пересчёта ячеек, зависящих от указанной.
     *
     * @param ref адрес изменённой ячейки
     * @return зависимые ячейки (сама ячейка не включается); каждая следует за теми,
     *         от которых зависит. Ячейки, входящие в цикл, помещаются в конец списка
     */
    public List<CellRef> topologicalOrder(CellRef ref) {
        return order(collectDependents(Objects.requireNonNull(ref, "ref")));
    }

    /**
     * Возвращает порядок пересчёта всех ячеек графа. Используется при полном пересчёте
     * документа после загрузки из базы данных.
     *
     * @return все ячейки графа в порядке зависимостей; участники циклов — в конце списка
     */
    public List<CellRef> topologicalOrder() {
        Set<CellRef> all = new LinkedHashSet<>(edges.keySet());
        all.addAll(dependents.keySet());
        return order(all);
    }

    private boolean visit(CellRef ref, Map<CellRef, Mark> marks) {
        Mark mark = marks.get(ref);
        if (mark == Mark.GRAY) {
            return true;
        }
        if (mark == Mark.BLACK) {
            return false;
        }
        marks.put(ref, Mark.GRAY);
        for (CellRef precedent : edges.getOrDefault(ref, Set.of())) {
            if (visit(precedent, marks)) {
                return true;
            }
        }
        marks.put(ref, Mark.BLACK);
        return false;
    }

    private Set<CellRef> collectDependents(CellRef ref) {
        Set<CellRef> affected = new LinkedHashSet<>();
        Deque<CellRef> queue = new ArrayDeque<>(dependents.getOrDefault(ref, Set.of()));
        while (!queue.isEmpty()) {
            CellRef current = queue.poll();
            if (current.equals(ref) || !affected.add(current)) {
                continue;
            }
            queue.addAll(dependents.getOrDefault(current, Set.of()));
        }
        return affected;
    }

    private List<CellRef> order(Set<CellRef> nodes) {
        Map<CellRef, Integer> incoming = new HashMap<>();
        for (CellRef node : nodes) {
            int count = 0;
            for (CellRef precedent : edges.getOrDefault(node, Set.of())) {
                if (nodes.contains(precedent)) {
                    count++;
                }
            }
            incoming.put(node, count);
        }
        PriorityQueue<CellRef> ready = new PriorityQueue<>(BY_POSITION);
        incoming.forEach((node, count) -> {
            if (count == 0) {
                ready.add(node);
            }
        });
        List<CellRef> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            CellRef node = ready.poll();
            result.add(node);
            for (CellRef dependent : dependents.getOrDefault(node, Set.of())) {
                if (incoming.containsKey(dependent) && incoming.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (result.size() < nodes.size()) {
            Set<CellRef> placed = new HashSet<>(result);
            nodes.stream().filter(node -> !placed.contains(node)).sorted(BY_POSITION).forEach(result::add);
        }
        return result;
    }

    /** Цвет вершины при обходе в глубину: белый цвет соответствует отсутствию отметки. */
    private enum Mark {
        GRAY,
        BLACK
    }
}
