package com.example.a3dmodel.exeption;

public class ProjectException extends Exception {
    public ProjectException() {

    }

    public ProjectException(String message){
        super(message);
    }

    public ProjectException(String message, Throwable cause){
        super(message, cause);
    }

    public ProjectException(Throwable cause){
        super(cause);
    }
}
