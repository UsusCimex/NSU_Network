package ru.nsu.SnakeGame;

import ru.nsu.SnakesProto.*;
import ru.nsu.patterns.Observable;
import ru.nsu.patterns.Observer;

import java.util.ArrayList;
import java.util.List;

public class GameField implements Observable {
    private final List<Observer> observers = new ArrayList<>();
    private final int width;
    private final int height;
    private final int foodCoefficientA;
    private final int foodCoefficientB;
    private final List<Snake> snakes;
    private final List<GameState.Coord> foods;

    public GameField(int width, int height, int foodCoefficientA, int foodCoefficientB) {
        this.width = width;
        this.height = height;
        this.foodCoefficientA = foodCoefficientA;
        this.foodCoefficientB = foodCoefficientB;
        this.snakes = new ArrayList<>();
        this.foods = new ArrayList<>();
    }
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public List<Snake> getSnakes() {
        return snakes;
    }
    public List<GameState.Coord> getFoods() {
        return foods;
    }
    public void addSnake(Snake snake) {
        snakes.add(snake);
        notifyObservers();
    }
    public int amountOfFoodNeeded(int playerCount) {
        return foodCoefficientA * playerCount + foodCoefficientB;
    }
    public void addFood(GameState.Coord food) {
        foods.add(food);
        notifyObservers();
    }
    public void removeFood(GameState.Coord food) {
        foods.remove(food);
        notifyObservers();
    }

    @Override
    public void registerObserver(Observer o) {
        observers.add(o);
    }

    @Override
    public void removeObserver(Observer o) {
        observers.remove(o);
    }

    @Override
    public void notifyObservers() {
        for (Observer observer : observers) {
            observer.update(this);
        }
    }
}
