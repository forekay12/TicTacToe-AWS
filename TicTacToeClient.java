//Name: Kayla Foremski
//CSCD 567 - Final Project
//Tic Tac Toe Client
//Uses Amazon SQS

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.Socket;
import java.net.InetAddress;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.List;
import java.util.Map.Entry;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

//import javafx.scene.paint.Color;

public class TicTacToeClient extends JFrame implements Runnable {
	private JTextField idField; 
    private JTextArea displayArea; 
    private JPanel boardPanel; 
    private JPanel panel2; 
    private Square board[][]; // tic-tac-toe board
    private Square currentSquare; 
	private String myMark; // this client's mark
	private boolean myTurn; 
    private final String X_MARK = "X"; 
    private final String O_MARK = "O"; 
    private final int bsize = 16;
    
    private AmazonSQS sqs;
    private String myMessageQueueUrl;
    private String messagesToXUrl;
    private String messagesToOUrl;
    private String locationToXQueueUrl;
    private String locationToOQueueUrl;
    private ArrayList<Integer> markedLocations;
    private boolean okToClick = false;
    private String b[][];
    public boolean gameOver = false;
    
    

   // set up user-interface and board
    public TicTacToeClient( String host ) { 
	   //AWS stuff
    	ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
    	try {
    		credentialsProvider.getCredentials();
    	} catch (Exception e) {
    		throw new AmazonClientException("Cannot load the credentials from the credential profiles file. " +
    				"Please make sure that your credentials file is at the correct " +
	                "location (~/.aws/credentials), and is in valid format.", e);
	    }

	    sqs = AmazonSQSClientBuilder.standard().withCredentials(credentialsProvider)
	    		.withRegion(Regions.US_WEST_2).build();
	     
	    try {
	    	// Create sqs queues 
	        CreateQueueRequest createQueueRequest = new CreateQueueRequest("MyMessageQueue");
	        myMessageQueueUrl = sqs.createQueue(createQueueRequest).getQueueUrl();
	        
	        CreateQueueRequest createQueueRequest1 = new CreateQueueRequest("MessagesToX");
	        messagesToXUrl = sqs.createQueue(createQueueRequest1).getQueueUrl();
	        
	        CreateQueueRequest createQueueRequest2 = new CreateQueueRequest("MessagesToO");
	        messagesToOUrl = sqs.createQueue(createQueueRequest2).getQueueUrl();
	         
	        CreateQueueRequest c = new CreateQueueRequest("LocationToXQueue");
	        locationToXQueueUrl = sqs.createQueue(c).getQueueUrl();
	        
	        CreateQueueRequest c1 = new CreateQueueRequest("LocationToOQueue");
	        locationToOQueueUrl = sqs.createQueue(c1).getQueueUrl();
	         
	    } catch (AmazonServiceException ase) {
	    	System.out.println("Caught an AmazonServiceException, which means your request made it " +
	                 "to Amazon SQS, but was rejected with an error response for some reason.");
	        System.out.println("Error Message:    " + ase.getMessage());
	        System.out.println("HTTP Status Code: " + ase.getStatusCode());
	        System.out.println("AWS Error Code:   " + ase.getErrorCode());
	        System.out.println("Error Type:       " + ase.getErrorType());
	        System.out.println("Request ID:       " + ase.getRequestId());
	    } catch (AmazonClientException ace) {
	        System.out.println("Caught an AmazonClientException, which means the client encountered " +
	                 "a serious internal problem while trying to communicate with SQS, such as not " +
	                 "being able to access the network.");
	        System.out.println("Error Message: " + ace.getMessage());
	    }
	    //END of AWS stuff
	   
	    displayArea = new JTextArea( 4, 30 ); 
	    displayArea.setEditable( false );
	    add( new JScrollPane( displayArea ), BorderLayout.SOUTH );

	    boardPanel = new JPanel(); 
	    boardPanel.setLayout( new GridLayout( bsize, bsize, 0, 0 ) ); 
	    board = new Square[ bsize ][ bsize ]; 

	    // loop over the rows in the board
	    for ( int row = 0; row < board.length; row++ ) {
	    	// loop over the columns in the board
	    	for ( int column = 0; column < board[ row ].length; column++ ) {
	    		// create square. initially the symbol on each square is a white space.
	    		board[ row ][ column ] = new Square( " ", row * bsize + column );
	    		boardPanel.add( board[ row ][ column ] ); // add square       
	    	} // end inner for
	    } // end outer for

	    idField = new JTextField(); 
	    idField.setEditable( false );
	    add( idField, BorderLayout.NORTH );
      
	    panel2 = new JPanel(); 
	    panel2.add( boardPanel, BorderLayout.CENTER ); 
	    add( panel2, BorderLayout.CENTER ); 

	    setSize( 600, 600 ); 
	    setVisible( true ); 
	    
	    markedLocations = new ArrayList<Integer>(0);
	    
	    //initialize array of board to all U's
	    b = new String [bsize][bsize];
	    for (int i = 0; i < b.length; i++) {
	    	String subArray[] = b[i];
	    	for (int k = 0; k < subArray.length; k++) {
	    		subArray[k] = "U";
	    	}
	    	
	    }
	    
	    startClient();
	} // end TicTacToeClient constructor


    public void startClient() {  
    	// create and start worker thread for this client
    	ExecutorService worker = Executors.newFixedThreadPool( 1 );
    	worker.execute( this ); // execute client
    } 

    // control thread that allows continuous update of displayArea
    public void run() {
    	
    	//Get player's mark (X or O). We hard coded here in demo. In your implementation, you may get this mark dynamically 
        //from the cloud service. This is the initial state of the game.
    		
    	
    	try {
			//try and dequeue message to determine whose turn it is
			ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(myMessageQueueUrl);
			List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
			
			//if message list is empty, we are first to connect
			if (messages.isEmpty()) {
				
				//Become player X
				myMark =  "X";
	    		displayMessage( "Player X connected.\nWaiting for another player.\n" );
	    		sqs.sendMessage(new SendMessageRequest(myMessageQueueUrl, "Waiting for player O"));
			
	    	//else, message list not empty, we are second to connect
			} else {
				
				//retrieve the message 
				for (Message message : messages) {
					String body = message.getBody();
					if (body.equals( "Waiting for player O" )) {
						
						//Become player O
						myMark =  "O";
		    			displayMessage( "Player O connected, please wait.\n" );
		    			sqs.sendMessage(new SendMessageRequest(messagesToXUrl, "Player O connected"));
		    			
		    			//then delete 
						String messageRecieptHandle = message.getReceiptHandle();
						sqs.deleteMessage(new DeleteMessageRequest(myMessageQueueUrl, messageRecieptHandle));
						
						//only process one message
						break;
					}
				}
			}
		

			if (myMark.equals( X_MARK )) {
				//Try and receive message stating player O connected
		    	ReceiveMessageRequest rMR = new ReceiveMessageRequest(messagesToXUrl);
		    	List<Message> mes = sqs.receiveMessage(rMR).getMessages();
					
				//if message list is empty, try again
				while (mes.isEmpty()) {
					rMR = new ReceiveMessageRequest(messagesToXUrl);
					mes = sqs.receiveMessage(rMR).getMessages();
				}
					
				//if not empty, retrieve the message
				for (Message m : mes) {
					String b = m.getBody();
					if ( b.equals( "Player O connected" ) )  {
							displayMessage( "Other player connected. Your move.\n" );
								
							//allow clicks and start turn
					    	okToClick = true;
		
					} 
								
					//then delete message
					String mRH = m.getReceiptHandle();
					sqs.deleteMessage(new DeleteMessageRequest(messagesToXUrl, mRH));
								
					//only process one message
					break;
				}
			}
 
		} catch (AmazonServiceException ase) {
			System.out.println("Caught an AmazonServiceException, which means your request made it " +
	                 "to Amazon SQS, but was rejected with an error response for some reason.");
			System.out.println("Error Message:    " + ase.getMessage());
	        System.out.println("HTTP Status Code: " + ase.getStatusCode());
	        System.out.println("AWS Error Code:   " + ase.getErrorCode());
	        System.out.println("Error Type:       " + ase.getErrorType());
	        System.out.println("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
	        System.out.println("Caught an AmazonClientException, which means the client encountered " +
	                 "a serious internal problem while trying to communicate with SQS, such as not " +
	                 "being able to access the network.");
	        System.out.println("Error Message: " + ace.getMessage());
	    } 
    		
    	SwingUtilities.invokeLater(new Runnable() {         
    		public void run() {
    			idField.setText( "You are player \"" + myMark + "\"" );
    		} 
    	}); 
         
    	myTurn = ( myMark.equals( X_MARK ) ); // determine if client's turn

    	// program the game logic below
    	while (!gameOver) {
    		
    		// Here in this while body, you will program the game logic. 
    	    // You are free to add any helper methods in this class or other classes.
    	    // Basically, this client player will retrieve a message from cloud in each while iteration
    	    // and process it until game over is detected.
    	    // Please check the processMessage() method below to gain some clues.
    			
    		try {	
	    		if (myMark.equals( X_MARK )) {
	    			//dequeue message from MessagesToX Queue
		    		ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(messagesToXUrl);
		    		List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
		    				
		    		for (Message message : messages) {
		    			String body = message.getBody();
		    			
		   				//then delete
		   				String messageRecieptHandle = message.getReceiptHandle();
		   				sqs.deleteMessage(new DeleteMessageRequest(messagesToXUrl, messageRecieptHandle));
		    				
		    			//process
		    			processMessage(body);
		    				
		    			//only process one message
						break;
		    		}
		    	           
	    		} else if (myMark.equals( O_MARK )) {
	    			//dequeue message from MessagesToO Queue
		    		ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(messagesToOUrl);
		    		List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
		    				
		    		for (Message message : messages) {
		    			String body = message.getBody();
		    				
		    			//then delete
		    			String messageRecieptHandle = message.getReceiptHandle();
		    			sqs.deleteMessage(new DeleteMessageRequest(messagesToOUrl, messageRecieptHandle));
		    				
		    			//process
		    			processMessage(body);
		    				
		    			//only process one message
						break;
		    		} 
	    		}	
    		} catch (AmazonServiceException ase) {
    			System.out.println("Caught an AmazonServiceException, which means your request made it " +
    		                "to Amazon SQS, but was rejected with an error response for some reason.");
    			System.out.println("Error Message:    " + ase.getMessage());
    		    System.out.println("HTTP Status Code: " + ase.getStatusCode());
    		    System.out.println("AWS Error Code:   " + ase.getErrorCode());
    		    System.out.println("Error Type:       " + ase.getErrorType());
    		    System.out.println("Request ID:       " + ase.getRequestId());
    		} catch (AmazonClientException ace) {
    		    System.out.println("Caught an AmazonClientException, which means the client encountered " +
    		                "a serious internal problem while trying to communicate with SQS, such as not " +
    		                "being able to access the network.");
    		    System.out.println("Error Message: " + ace.getMessage());
    		}
    		
    	} // end while

	} // end method run
   
   // You have write this method that checks the game board to detect winning status.
	private boolean isGameOver() {
		int n = 5;                                                          
		int row = 0;
		int col = 0;
		int i = 0;
		ArrayList<TicTacToeClient.Square> l = new ArrayList<TicTacToeClient.Square>();
		
		//check for X winner
		//check rows
		for ( row = 0; row < bsize; row++) {                       
			for ( col = 0; col < (bsize-(n-1)); col++) {         
		    	while (b[row][col].equals("X")) {      
		        	l.add(board[row][col]);
		    		col++;
		        	i++;
		            if (i == n) {
		            	//report win for X
			            okToClick = false;
			            gameOver = true;
			            // highlight the winning squares
			            for (Square s : l) {
			            	s.setBackground(Color.GREEN);
			            }
			            displayMessage( "Game over, Player X won!\n" );
			            return true;
		            }
		        }
		        i = 0;
		        l.clear();
		    }
		}
		    
		//checks columns
		for ( col = 0; col < bsize; col++) {
		    for ( row = 0; row < (bsize-(n-1)); row++) {
		        while (b[row][col].equals("X")) {
		            l.add(board[row][col]);
		        	row++;
		            i++;
		            if (i == n) {
		                //report win for X
			            okToClick = false;
			            gameOver = true;
			            // highlight the winning squares
			            for (Square s : l) {
			            	s.setBackground(Color.GREEN);
			            }
			            displayMessage( "Game over, Player X won!\n" );
			            return true;
		            }
		        }
		        i = 0;
		        l.clear();
		    }
		}
		    
		//checks diagonals
		for ( col = 0; col < (bsize - (n-1)); col++) {          
		    for ( row = 0; row < (bsize-(n-1)); row++) {
		        while (b[row][col].equals("X")) {
		        	l.add(board[row][col]);
		            row++;
		            col++;
		            i++;
		            if (i == n) {
		                //report win for X
			            okToClick = false;
			            gameOver = true;
			            // highlight the winning squares
			            for (Square s : l) {
			            	s.setBackground(Color.GREEN);
			            }
			            displayMessage( "Game over, Player X won!\n" );
			            return true;
		            }
		        }
		        i = 0;
		        l.clear();
		    }
		}
		    
		//checks anti-diagonals
		for ( col = bsize-1; col > 0+(n-2); col--) {                                                           
		    for ( row = 0; row < (bsize-(n-1)); row++) {       
		        while (b[row][col].equals("X")) {
		        	l.add(board[row][col]);
		            row++;                                  
		            col--;                                  
		            i++;                                    
		            if (i == n) {                            
		                //report win for X
			            okToClick = false;
			            gameOver = true;
			            // highlight the winning squares
			            for (Square s : l) {
			            	s.setBackground(Color.GREEN);
			            }
			            displayMessage( "Game over, Player X won!\n" );
			            return true;                      
		            }                                       
		        }                                           
		        i = 0;
		        l.clear();
		    }                                               
		}                                          
		
		//check for O win
		//check rows
		for ( row = 0; row < bsize; row++) {                       
		    for ( col = 0; col < (bsize-(n-1)); col++) {         
		        while (b[row][col].equals("O")) { 
		        	l.add(board[row][col]);
		            col++;
		            i++;
		            if (i == n) {
		            	//report win for O
		                okToClick = false;
		                gameOver = true;
		                // highlight the winning squares
			            for (Square s : l) {
			            	s.setBackground(Color.GREEN);
			            }
		            	displayMessage( "Game over, Player O won!\n" );
		            	return true;
		            }
		        }
		        i = 0;
		        l.clear();
		    }
		}
		    
		//checks columns
		for ( col = 0; col < bsize; col++) {
		    for ( row = 0; row < (bsize-(n-1)); row++) {
		        while (b[row][col].equals("O")) {
		        	l.add(board[row][col]);
		            row++;
		            i++;
		            if (i == n) {
		                //report win for O
		                okToClick = false;
		                gameOver = true;
		                // highlight the winning squares
			            for (Square s : l) {
			            	s.setBackground(Color.GREEN);
			            }
		            	displayMessage( "Game over, Player O won!\n" );
		            	return true;
		            }
		        }
		        i = 0;
		        l.clear();
		    }
		}
		    
		//checks diagonals
		for ( col = 0; col < (bsize - (n-1)); col++) {          
		    for ( row = 0; row < (bsize-(n-1)); row++) {
		        while (b[row][col].equals("O")) {
		        	l.add(board[row][col]);
		            row++;
		            col++;
		            i++;
		            if (i == n) {
		                //report win for O
		                okToClick = false;
		                gameOver = true;
		                // highlight the winning squares
			            for (Square s : l) {
			            	s.setBackground(Color.GREEN);
			            }
		            	displayMessage( "Game over, Player O won!\n" );
		            	return true;
		            }
		        }
		        i = 0;
		        l.clear();
		    }
		}
		    
		//checks anti-diagonals
		for ( col = bsize-1; col > 0+(n-2); col--) {                                                           
		    for ( row = 0; row < (bsize-(n-1)); row++) {       
		        while (b[row][col].equals("O")) { 
		        	l.add(board[row][col]);
		            row++;                                  
		            col--;                                  
		            i++;                                    
		            if (i == n) {                            
		            	//report win for O
		                okToClick = false;
		                gameOver = true;
		                // highlight the winning squares
			            for (Square s : l) {
			            	s.setBackground(Color.GREEN);
			            }
		            	displayMessage( "Game over, Player O won!\n" );
		            	return true;                      
		            }                                       
		        }                                           
		        i = 0;
		        l.clear();
		    }                                               
		}
		    
		//else
		return false;
	}



	// This method is not used currently, but it may give you some hints regarding
	// how one client talks to other client through cloud service(s).
	private void processMessage( String message ) {
		
		if ( message.equals( "Opponent moved" ) )  {
			int location = getOpponentMove(); // Here get move location from opponent						
			int row = location / bsize; // calculate row
			int column = location % bsize; // calculate column
			setMark(board[row][column], (myMark.equals(X_MARK) ? O_MARK : X_MARK)); // mark move
			
			//mark the location on internal 1D array 
			markedLocations.add(location);
			
			//mark the location on internal 2D array 
			if (myMark.equals(X_MARK)) {
				b[row][column] = O_MARK;
			} else {
				b[row][column] = X_MARK;
			}
			
			if (isGameOver()) {
				gameOver = true;
				return;
			}
			
			displayMessage( "Opponent moved. Your turn.\n" );
			myTurn = true; // now this client's turn
			okToClick = true;
		
		} else {
			displayMessage( message + "\n" ); // display the message
		}
	} 

	//Here get move location from opponent
	private int getOpponentMove() {
		// Please write your code here
		
		int location = -1;
		
		try {
			
			if (myMark.equals( X_MARK )) {
				
				//dequeue message from LocationToX Queue
				ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(locationToXQueueUrl);
				List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
				
				//while message list is empty, try again
				while (messages.isEmpty()) {
					receiveMessageRequest = new ReceiveMessageRequest(locationToXQueueUrl);
					messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
				}
				
				for (Message message : messages) {
					String body = message.getBody();
					location = Integer.parseInt(body);
					//delete after dequeued
					String messageRecieptHandle = message.getReceiptHandle();
					sqs.deleteMessage(new DeleteMessageRequest(locationToXQueueUrl, messageRecieptHandle));
					
					//only process one message
					break;
				}
    	           
			} else if (myMark.equals( O_MARK )) {
				//dequeue message from LocationToO Queue
				ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(locationToOQueueUrl);
				List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
				
				//while message list is empty, try again
				while (messages.isEmpty()) {
					receiveMessageRequest = new ReceiveMessageRequest(locationToOQueueUrl);
					messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
				}
				
				for (Message message : messages) {
					String body = message.getBody();
					location = Integer.parseInt(body);
					//delete after dequeued
					String messageRecieptHandle = message.getReceiptHandle();
					sqs.deleteMessage(new DeleteMessageRequest(locationToOQueueUrl, messageRecieptHandle));
					
					//only process one message
					break;
				} 
			}
			
			
		} catch (AmazonServiceException ase) {
			System.out.println("Caught an AmazonServiceException, which means your request made it " +
	                 "to Amazon SQS, but was rejected with an error response for some reason.");
			System.out.println("Error Message:    " + ase.getMessage());
	        System.out.println("HTTP Status Code: " + ase.getStatusCode());
	        System.out.println("AWS Error Code:   " + ase.getErrorCode());
	        System.out.println("Error Type:       " + ase.getErrorType());
	        System.out.println("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
	        System.out.println("Caught an AmazonClientException, which means the client encountered " +
	                 "a serious internal problem while trying to communicate with SQS, such as not " +
	                 "being able to access the network.");
	        System.out.println("Error Message: " + ace.getMessage());
	    }   
		
		return location;
	}
   
	private void displayMessage( final String messageToDisplay ) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				displayArea.append( messageToDisplay ); 
			} 
		}); 
	} 

	private void setMark( final Square squareToMark, final String mark ) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				squareToMark.setMark( mark ); // set mark in square
            } 
         }); 
	} 

	// Send message to cloud service indicating clicked square
	public void sendClickedSquare( int location ) {
      
		if (myTurn) {
			// Below you send the clicked location to the cloud service that will notify the opponent,
			// Or the opponent will retrieve the move location itself.
			// Please write your own code below.
			
			try {
				if (myMark.equals( X_MARK )) {
					sqs.sendMessage(new SendMessageRequest(messagesToOUrl, "Opponent moved"));
					sqs.sendMessage(new SendMessageRequest(locationToOQueueUrl, Integer.toString(location)));
				} else if (myMark.equals( O_MARK )) {
					sqs.sendMessage(new SendMessageRequest(messagesToXUrl, "Opponent moved"));
					sqs.sendMessage(new SendMessageRequest(locationToXQueueUrl, Integer.toString(location)));
				}
    	  
			} catch (AmazonServiceException ase) {
				System.out.println("Caught an AmazonServiceException, which means your request made it " +
		                 "to Amazon SQS, but was rejected with an error response for some reason.");
				System.out.println("Error Message:    " + ase.getMessage());
		        System.out.println("HTTP Status Code: " + ase.getStatusCode());
		        System.out.println("AWS Error Code:   " + ase.getErrorCode());
		        System.out.println("Error Type:       " + ase.getErrorType());
		        System.out.println("Request ID:       " + ase.getRequestId());
			} catch (AmazonClientException ace) {
		        System.out.println("Caught an AmazonClientException, which means the client encountered " +
		                 "a serious internal problem while trying to communicate with SQS, such as not " +
		                 "being able to access the network.");
		        System.out.println("Error Message: " + ace.getMessage());
		    }
			
			myTurn = false; 
			
			if (isGameOver()) {
				gameOver = true;
				return;
			}
		} 
	} 

	public void setCurrentSquare( Square square ) {
		currentSquare = square; 
	} 
   
	// private inner class for the squares on the board
	private class Square extends JPanel {
		private String mark; 
		private int location; 
	   
		public Square( String squareMark, int squareLocation ) {
			mark = squareMark; 
			location = squareLocation; 
	
			addMouseListener( new MouseAdapter() {
				public void mouseReleased( MouseEvent e ) {
					if (okToClick) {
						setCurrentSquare( Square.this ); 
		                  
						// You may have to send location of this square to 
						// the cloud service that will notify the opponent player.
						// you have write your own method isValidMove().
		                  
						if (isValidMove()) {
							TicTacToeClient.this.setMark( currentSquare, myMark );
							displayMessage("Valid move. Please wait.\n");
							markedLocations.add(getSquareLocation());
							
							int row = getSquareLocation() / bsize; 
							int column = getSquareLocation() % bsize;
							
							//mark the location on internal 2D array 
							if (myMark.equals(X_MARK)) {
								b[row][column] = X_MARK;
							} else {
								b[row][column] = O_MARK;
							}
							
							sendClickedSquare(getSquareLocation());
							okToClick = false;
						}
					}
				} 
			}); 
		} // end Square constructor
	
		public boolean isValidMove() {
			//ADD CODE HERE
			for (int i = 0; i < markedLocations.size(); i++) {
				if (getSquareLocation() == markedLocations.get(i)) {
					return false;
				}
			}
			return true;
		}
	      
		public Dimension getPreferredSize() { 
			return new Dimension( 30, 30 ); 
		} 
	
		public Dimension getMinimumSize() {
			return getPreferredSize(); 
		} 
	
		public void setMark( String newMark ) { 
			mark = newMark; 
			repaint(); 
		} 
	   
		public int getSquareLocation() {
			return location; 
		} 
	   
		public void paintComponent( Graphics g ) {
			super.paintComponent( g );
		
			g.drawRect( 0, 0, 29, 29 ); 
			g.drawString( mark, 11, 20 );   
		}
		
	} // end inner-class Square
   
	public static void main( String args[] ) {
		TicTacToeClient application; 
     
		// if no command line args
		if ( args.length == 0 )
			application = new TicTacToeClient( "" );  
		else
			application = new TicTacToeClient( args[ 0 ] ); 

		application.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
	} 

} 


