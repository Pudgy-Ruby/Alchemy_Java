package com.example.myapplication;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.Utils;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Uint;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;
import org.web3j.utils.Strings;

import com.example.myapplication.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String apiKey = "wHC4H7cdGZbbAP-XDqeo6MNcyL0K3V3R"; // Alchemy

        // Set the Alchemy API endpoint
        String apiUrl = "https://eth-mainnet.alchemyapi.io/v2/" + apiKey;

        // Set the contract address
        String contractAddress = "0xfc68f2130e094c95b6c4f5494158cbeb172e18a0";

        // Set the asset category
        String assetCategory = "erc721";

        String data = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"alchemy_getAssetTransfers\",\"params\":[{\"fromBlock\":\"0x0\",\"contractAddresses\":[\"" + contractAddress + "\"],\"excludeZeroValue\":false,\"category\":[\"" + assetCategory + "\"]}]}";

        // Create the request body
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), data);

        // Build the request
        Request request = new Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build();


        // Send the HTTP request
        OkHttpClient client = new OkHttpClient();
        // Send the request asynchronously
        client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String responseBody = response.body().string();

                        try {
                            JSONObject json = new JSONObject(responseBody);
                            JSONObject results = json.getJSONObject("result");
                            JSONArray transfers = results.getJSONArray("transfers");

                            int nftId = 1;

                            // Filter the transfers based on the NFT ID
                            JSONArray filteredTransfers = new JSONArray();
                            for (int i = 0; i < transfers.length(); i++) {
                                JSONObject transfer = transfers.getJSONObject(i);
                                String erc721TokenId = transfer.getString("erc721TokenId");
                                String cleanTokenId = erc721TokenId.replace("0x", "");
                                if (new BigInteger(cleanTokenId, 16).intValue() == nftId) {
                                    filteredTransfers.put(transfer);
                                }
                            }

                            System.out.println("transfers: " + transfers);

                            JSONArray exercises = new JSONArray();
                            for (int i = 0; i < filteredTransfers.length(); i++) {
                                JSONObject transfer = filteredTransfers.getJSONObject(i);
                                String to = transfer.getString("to");
                                if (to.equals("0x0000000000000000000000000000000000000000")) {
                                    exercises.put(transfer);
                                }
                            }

                            System.out.println("exercises: " + exercises);

                            if (exercises.length() > 0) {
                                String transactionHash = exercises.getJSONObject(0).getString("hash");

                                System.out.println("transactionHash" + transactionHash);

                                String infuraKey = "eac2c2abc23b40629f211fa251f3d813";
                                Web3j web3j = Web3j.build(new HttpService("https://mainnet.infura.io/v3/" + infuraKey));

                                EthTransaction transaction = web3j.ethGetTransactionByHash(transactionHash).send();
                                String inputData = transaction.getTransaction().get().getInput().substring(10);;

                                String poolAddress = transaction.getTransaction().get().getTo();

                                // Define the return types to decode
                                List<TypeReference<?>> outputParameters = new ArrayList<>();
                                outputParameters.add(new TypeReference<Uint>() {});
                                outputParameters.add(new TypeReference<Uint>() {});

                                // Decode the input data
                                List<Type> decodedInput = FunctionReturnDecoder.decode(inputData, Utils.convert(outputParameters));

                                // Get the decoded values
                                BigInteger optionId = (BigInteger) decodedInput.get(0).getValue();
                                BigInteger tokenId = (BigInteger) decodedInput.get(1).getValue();

                                System.out.println("optionId: " + optionId);
                                System.out.println("poolAddress: " + poolAddress);


                                // Create the Function object for the desired view function
                                String functionName = "getOptionData";
                                List<Type> inputParameters = Arrays.asList(new Uint256(nftId));
                                List<TypeReference<?>> outputParameter = Arrays.asList(new TypeReference<Bool>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {},new TypeReference<Uint256>() {},new TypeReference<Uint256>() {});
                                Function function = new Function(functionName, inputParameters, outputParameter);

                                // Encode the function call
                                String encodedFunction = FunctionEncoder.encode(function);

                                // Create the Call object to execute the function call
                                Transaction callTransaction = Transaction.createEthCallTransaction(null, poolAddress, encodedFunction);

                                // Send the call to the Ethereum network
                                EthCall responses = web3j.ethCall(callTransaction, DefaultBlockParameterName.LATEST).send();

                                // Parse and handle the response
                                String result = responses.getResult();
                                List<Type> decodedResult = FunctionReturnDecoder.decode(result, function.getOutputParameters());

                                // Access the return value
                                for (int i = 0; i < decodedResult.size(); i++) {
                                    System.out.println("OptionData " + decodedResult.get(i).getValue());
                                }


                                // Get NFT Address

                                // Create the Function object for the desired view function
                                String getNftAddress = "getNftAddress";
                                List<TypeReference<?>> getNftAddressOutputParameter = Arrays.asList(new TypeReference<Utf8String>() {});
                                Function getNftAddressFunction = new Function(getNftAddress, Collections.emptyList(), getNftAddressOutputParameter);

                                // Encode the function call
                                String encodedGetNftAddressFunction = FunctionEncoder.encode(getNftAddressFunction);

                                // Create the Call object to execute the function call
                                Transaction callGetNftAddressTransaction = Transaction.createEthCallTransaction(null, poolAddress, encodedGetNftAddressFunction);

                                // Send the call to the Ethereum network
                                EthCall getNftAddressResponses = web3j.ethCall(callGetNftAddressTransaction, DefaultBlockParameterName.LATEST).send();

                                // Parse and handle the response
                                String getNftAddressResult = getNftAddressResponses.getResult();
                                String nftAddress = new Address(getNftAddressResult).toString();

                                System.out.println("nftAddress: " + nftAddress);
                                System.out.println("tokenId: " + tokenId.toString());

                                OkHttpClient client = new OkHttpClient();

                                String url = "https://api.reservoir.tools/tokens/" + nftAddress + "%3A" +tokenId + "/activity/v5?types=sale&types=mint";
                                Request request = new Request.Builder()
                                        .url(url)
                                        .get()
                                        .addHeader("accept", "*/*")
                                        .addHeader("x-api-key", "29933ebc-4aa9-5b0f-a55c-8568f8668120")
                                        .build();

                                Response responseForActivity = client.newCall(request).execute();
                                String activityBody = responseForActivity.body().string();

                                System.out.println("activityBody: " + activityBody);
                                JSONObject activityJson = new JSONObject(activityBody);
                                JSONArray activities = activityJson.getJSONArray("activities");

                                String txHash = activities.getJSONObject(0).getString("txHash");
                                String prices = activities.getJSONObject(0).getString("price");
                                System.out.println("txHash: " + txHash);
                                System.out.println("prices: " + prices);

                                // Get Block Number from txHash

                                EthTransaction tx = web3j.ethGetTransactionByHash(txHash).send();
                                BigInteger fromBlock = tx.getTransaction().get().getBlockNumber();
                                System.out.println("fromBlock: " + fromBlock);

                                String data = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"alchemy_getAssetTransfers\",\"params\":[{\"fromBlock\":\"" + "0x" + Integer.toHexString(fromBlock.intValue()) + "\",\"contractAddresses\":[\"" + contractAddress + "\"],\"excludeZeroValue\":false,\"category\":[\"" + assetCategory + "\"]}]}";

                                // Create the request body
                                MediaType mediaType = MediaType.parse("application/json");
                                RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), data);

                                // Build the request
                                Request requestForAssetsTransfers = new Request.Builder()
                                        .url(apiUrl)
                                        .post(requestBody)
                                        .addHeader("Content-Type", "application/json")
                                        .build();

                                Response assetTransferResponse = client.newCall(requestForAssetsTransfers).execute();
                                String assetTransferBody = assetTransferResponse.body().string();

                                JSONObject assetTransferJson = new JSONObject(assetTransferBody);
                                JSONObject assetTransferResults = assetTransferJson.getJSONObject("result");
                                JSONArray assetTransfers = assetTransferResults.getJSONArray("transfers");

                                System.out.println("assetTransfers: " + assetTransfers.length());
                                System.out.println("assetTransfers: " + assetTransfers);

                            }


                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }

                    }

                    @Override
                    public void onFailure(Call call, IOException e) {
                        e.printStackTrace();
                    }
                });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_main, menu);

        Thread thread = new Thread(){
            public void run(){
                System.out.println("Thread Running");
            }
        };

        thread.start();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}