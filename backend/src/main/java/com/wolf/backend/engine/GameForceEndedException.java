package com.wolf.backend.engine;

/**
 * 房主/管理员主动结束对局时，从obtainAction()的检查点抛出，沿调用栈一路冒泡到runGameLoop()，
 * 让当前正在执行的阶段方法（无论嵌套多深）尽快中止并转入结算，而不用在每个循环里手动加判断。
 */
public class GameForceEndedException extends RuntimeException {
}
